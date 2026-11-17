import assert from 'node:assert/strict'
import { readFileSync, writeFileSync, readdirSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { pathToFileURL } from 'node:url'
const root='/tmp/lexaid-full-review'
const repo='/Users/yangyongze/Desktop/lexaid/lexaid'
const head=execFileSync('git',['show','HEAD:scripts/build-public-question-dataset.mjs'],{cwd:repo,encoding:'utf8'})
const helpers=head.slice(head.indexOf('function cleanText('),head.indexOf('function readJsonLines(')).replace('function toQuestion(', 'export function toQuestion(')
writeFileSync(root+'/importer-original-helpers.mjs',helpers)
const {toQuestion:before}=await import(pathToFileURL(root+'/importer-original-helpers.mjs'))
const {toQuestion:after}=await import(pathToFileURL(repo+'/scripts/build-public-question-dataset.mjs'))
const proposals=new Map(readFileSync(root+'/lexeval-corrections.jsonl','utf8').trim().split('\n').map(JSON.parse).map(x=>[x.upstreamSource,x]))
const expectedSourceIds=new Set([...proposals.values()].map(x=>{
 const m=x.upstreamSource.match(/\/data\/(\d_\d)\.json#L(\d+)/); return m[1]+':'+String(Number(m[2])-1).padStart(4,'0')
}))
const pick=x=>Object.fromEntries(['type','stem','options','answer'].map(k=>[k,x[k]]))
const sha=x=>createHash('sha256').update(x).digest('hex')
const counts={LawBench:{records:0,unchanged:0,changed:0,choiceBefore:0,choiceAfter:0,nativeWrapper:0,shortPreserved:0},LexEval:{records:0,unchanged:0,changed:0,choiceBefore:0,choiceAfter:0,nativeWrapper:0,shortPreserved:0}}
const taskCounts={}
const details=[], exceptions=[], manifest=[]
for(const [dataset,dir]of[['LawBench','lawbench-source'],['LexEval','lexeval-upstream']]) {
 const files=readdirSync(root+'/'+dir).filter(x=>/^\d[-_]\d+\.json$/.test(x)).sort()
 for(const name of files){
  const raw=readFileSync(root+'/'+dir+'/'+name,'utf8')
  manifest.push({dataset,file:name,sha256:sha(raw)})
  const items=dataset==='LawBench'?JSON.parse(raw):raw.trim().split('\n').map(JSON.parse)
  const taskCount={records:0,unchanged:0,changed:0};taskCounts[dataset+'/'+name]=taskCount
  items.forEach((item,index)=>{
   const input={question:dataset==='LawBench'?item.question:item.input,answer:item.answer,instruction:item.instruction,category:'法律综合',difficulty:3}
   const old=before(input),now=after(input),sourceItemId=name.replace('.json','')+':'+String(index).padStart(4,'0')
   const same=JSON.stringify(pick(old))===JSON.stringify(pick(now)); const c=counts[dataset]
   c.records++; c[same?'unchanged':'changed']++; c.choiceBefore+=old.type!=='SHORT';c.choiceAfter+=now.type!=='SHORT'
   taskCount.records++;taskCount[same?'unchanged':'changed']++
   const rawAnswer=String(item.answer??'').trim()
   if(/^\[正确答案\].*<eoa>$/i.test(rawAnswer))c.nativeWrapper++
   if(old.type==='SHORT'&&now.type==='SHORT')c.shortPreserved++
   if(!same)details.push({dataset,sourceItemId,old:pick(old),current:pick(now)})
   if(dataset==='LexEval'&&!name.startsWith('5_')&&now.type==='SHORT')exceptions.push({dataset,sourceItemId,originalAnswer:item.answer,reason:rawAnswer?'ANNOTATED_ANSWER_REMAINS_UNPARSED':'UPSTREAM_EMPTY_ANSWER'})
   assert.equal(now.analysis,null,'task instruction must stay out of teaching analysis')
   if(dataset==='LawBench')assert.ok(same,`LawBench regression ${sourceItemId}`)
   else assert.equal(!same,expectedSourceIds.has(sourceItemId),`Unexpected LexEval change ${sourceItemId}`)
  })
 }
}
assert.equal(counts.LawBench.records,10000);assert.equal(counts.LexEval.records,14150);assert.equal(details.length,87)
assert.equal(counts.LawBench.nativeWrapper,500);assert.equal(exceptions.length,9)
const report={passed:true,totalRecords:24150,totalUnchanged:24063,expectedStructuralRecoveries:87,unexpectedRegressions:0,baseline:'git HEAD parseChoice/toQuestion structural fields; analysis intentionally excluded because instructions are removed',baselineCommit:execFileSync('git',['rev-parse','HEAD'],{cwd:repo,encoding:'utf8'}).trim(),baselineHelperSha256:sha(helpers),counts,taskCounts,conservativeExceptions:exceptions,upstreamCommits:{LawBench:'e30981bb3ff54c41571f222e0b23e92d27375388',LexEval:'044c695f62894deef41ad9a30797e1da0404945c'},files:manifest,importerSha256:sha(readFileSync(repo+'/scripts/build-public-question-dataset.mjs')),changedSources:details.map(({dataset,sourceItemId})=>({dataset,sourceItemId}))}
writeFileSync(root+'/complete-importer-comparison.json',JSON.stringify(report,null,2)+'\n')
writeFileSync(root+'/complete-importer-changes.jsonl',details.map(JSON.stringify).join('\n')+'\n')
console.log(JSON.stringify({passed:report.passed,total:report.totalRecords,unchanged:report.totalUnchanged,recovered:87,regressions:0,counts,conservativeExceptions:exceptions},null,2))
