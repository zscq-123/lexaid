#!/usr/bin/env python3
"""Merge dataset audit snapshots without equating source/format checks with legal approval."""
import argparse
from collections import Counter
import csv
import gzip
import hashlib
import json
from pathlib import Path


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--input', required=True, type=Path)
    ap.add_argument('--baseline', required=True, type=Path)
    ap.add_argument('--current', type=Path, help='Post-correction quality manifest; audit records retain their original fingerprints')
    ap.add_argument('--revisions', type=Path, help='Verified applied revision records from question_content_revision')
    ap.add_argument('--formal', type=Path, help='Separate mock-exam audit; never counted as daily practice questions')
    ap.add_argument('--output', required=True, type=Path)
    args = ap.parse_args()
    baseline = {row['id']: row for row in map(json.loads, gzip.decompress(args.baseline.read_bytes()).decode().splitlines())}
    current = baseline if args.current is None else {row['id']: row for row in map(json.loads, gzip.decompress(args.current.read_bytes()).decode().splitlines())}
    assert set(current) == set(baseline), 'Current question IDs differ from the audit baseline'
    revisions = {} if args.revisions is None else {row['id']: row for row in json.loads(args.revisions.read_text())}
    changed_ids = {qid for qid in baseline if current[qid]['contentHash'] != baseline[qid]['contentHash']}
    assert changed_ids == set(revisions), 'Changed content must have exactly one verified applied revision record'
    merged = {}
    args.output.mkdir(parents=True, exist_ok=True)
    for name in ['seed', 'lawbench', 'lexeval', 'lecoqa']:
        raw = (args.input / (name + '.jsonl')).read_bytes()
        rows = list(map(json.loads, raw.decode().splitlines()))
        assert len({r.get('id', r.get('question_id')) for r in rows}) == len(rows), name + ': duplicate IDs'
        (args.output / (name + '.jsonl.gz')).write_bytes(gzip.compress(raw, mtime=0))
        for row in rows:
            qid = row.get('id', row.get('question_id'))
            fingerprint = row.get('contentHash', row.get('content_hash'))
            assert qid in baseline and fingerprint == baseline[qid]['contentHash'], f'Stale or foreign audit: {name}/{qid}'
            entry = merged.setdefault(qid, {'id': qid, 'contentHash': fingerprint, 'reviews': []})
            entry['reviews'].append({'dataset': name, 'record': row})
    assert set(merged) == set(baseline), f'Missing: {sorted(set(baseline)-set(merged))[:30]}'
    daily_count = len(merged)
    formal_rows = [] if args.formal is None else list(map(json.loads, args.formal.read_text().splitlines()))
    assert len({r['id'] for r in formal_rows}) == len(formal_rows), 'Duplicate formal exam IDs'
    for row in formal_rows:
        qid = row['id']
        assert qid not in merged, f'Cross-table ID collision: {qid}'
        merged[qid] = {'id': qid, 'contentHash': row['contentHash'], 'reviews': [{'dataset': 'formal', 'record': row}]}
        current[qid] = {'contentHash': row['currentContentHash']}
        if row.get('appliedRevision'): revisions[qid] = row['appliedRevision']
    if args.formal is not None:
        (args.output / 'formal.jsonl.gz').write_bytes(gzip.compress(args.formal.read_bytes(), mtime=0))
    compact = []
    for qid, entry in sorted(merged.items()):
        sources, decisions, issues, actions, urls, methods = [], [], [], [], [], []
        stem = ''
        for review in entry['reviews']:
            row = review['record']; sources.append(review['dataset'])
            if review['dataset'] == 'seed':
                methods.append('seed: AI 完整阅读题干、选项、答案及解析；依据访问范围见逐题记录')
            elif review['dataset'] == 'lawbench':
                methods.append('LawBench: 全字段程序比对；' + ('AI 完整阅读，法律推理未核实' if row.get('manual_content_reading', {}).get('performed') else '未逐题阅读全文或核实法律推理'))
            elif review['dataset'] == 'lexeval':
                if row.get('independentFullContentRead'):
                    methods.append('LexEval: 独立 AI 完整阅读并核对结构修复；法律答案未整体批准')
                else:
                    methods.append('LexEval: 全字段程序比对；' + ('AI 阅读摘录，法律答案未核实' if row.get('assessmentScope', {}).get('analystExcerptInspected') else '未逐题阅读全文或核实法律答案'))
            elif review['dataset'] == 'formal':
                methods.append('模拟考试: ' + row['reviewMethod'])
            else:
                reading = row.get('modelReading', {})
                methods.append('LeCoQA: ' + str(reading.get('status', 'NOT_INDIVIDUALLY_READ')) + '；' + str(reading.get('scope') or '未逐题实质核验'))
            if review['dataset'] in {'lecoqa', 'seed', 'formal'}:
                decisions.append(str(row['status']))
            else:
                decisions.append(str(row.get('decision', row.get('disposition', 'UNSPECIFIED'))))
            stem = stem or row.get('stem', row.get('evidence', {}).get('stem_excerpt', row.get('evidence', {}).get('stemExcerpt', '')))
            for issue in row.get('issues', row.get('reasonCodes', row.get('flags', []))):
                issues.append(issue if isinstance(issue, str) else issue.get('code', issue.get('type', json.dumps(issue, ensure_ascii=False))))
            action = row.get('action', row.get('reasoning', ''))
            if action: actions.append(action)
            actions += row.get('actions', row.get('unresolved', []))
            urls += row.get('sourceUrls', row.get('upstream_urls', []))
            urls += [r['upstreamUrl'] for r in row.get('sourceVerification', []) if r.get('upstreamUrl')]
            for citation in row.get('citationChecks', []):
                if citation.get('officialUrl'): urls.append(citation['officialUrl'])
        compact.append({'id': qid, 'sources': sources, 'decisions': decisions, 'stem': stem,
                        'issues': sorted(set(issues)), 'actions': actions, 'urls': sorted(set(urls)), 'methods': methods,
                        'contentHash': entry['contentHash'], 'currentContentHash': current[qid]['contentHash'],
                        'appliedRevision': revisions.get(qid)})
    data = ''.join(json.dumps(row, ensure_ascii=False) + '\n' for row in compact).encode()
    (args.output / 'index.jsonl.gz').write_bytes(gzip.compress(data, mtime=0))
    with (args.output / 'index.csv').open('w', encoding='utf-8-sig', newline='') as stream:
        writer = csv.writer(stream)
        writer.writerow(['题号', '来源', '处置', '题干摘录', '问题代码', '处理建议', '核验范围', '审核时内容指纹', '当前内容指纹', '已归档修订'])
        for row in compact:
            cells = [str(row['id']), '; '.join(row['sources']), '; '.join(row['decisions']), row['stem'],
                     '; '.join(row['issues']), '; '.join(row['actions']), '; '.join(row['methods']), row['contentHash'],
                     row['currentContentHash'], str((row['appliedRevision'] or {}).get('revisionKey', ''))]
            writer.writerow(["'" + s if s.startswith(('=', '+', '-', '@', '\t', '\r')) else s for s in cells])
    summary = {'uniqueQuestions': len(compact), 'datasetReviewRecords': sum(len(e['reviews']) for e in merged.values()),
               'dailyPracticeQuestions': daily_count, 'separateFormalExamQuestions': len(formal_rows),
               'multiSourceQuestions': sum(len(e['reviews']) > 1 for e in merged.values()),
               'countsByDataset': dict(Counter(r['dataset'] for e in merged.values() for r in e['reviews'])),
               'dailyChangedAfterAuditSnapshot': len(changed_ids), 'formalChangedAfterAuditSnapshot': sum(bool(r.get('appliedRevision')) for r in formal_rows),
               'indexSha256': hashlib.sha256(data).hexdigest(), 'humanDualApproval': False,
               'scope': 'All rows have content/source audit records. This does not mean every legal answer was substantively verified.'}
    (args.output / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
    embedded = json.dumps(compact, ensure_ascii=False).replace('<', '\\u003c')
    html = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>LexAid 全量题库审计</title><style>body{font:15px/1.65 system-ui;margin:32px auto;padding:0 20px;max-width:1150px;background:#f7f9fc;color:#1c2940}h1{font-size:26px}input,select,button{font:inherit;padding:8px;border:1px solid #bdc8d8;border-radius:7px;margin:4px}input{width:min(440px,90%)}article{background:white;border:1px solid #dbe2ee;border-radius:10px;margin:15px 0;padding:16px;overflow-wrap:anywhere}small{color:#57677e}pre{white-space:pre-wrap}a{color:#215eab}#summary{padding:12px;background:#e9eff9;border-radius:8px}</style>
<h1>LexAid 全量题库审计</h1><p id="summary">全量记录覆盖与法律实质核验分别记录。本页没有人工双签或自动放行结论；完整逐题证据见旁边各数据集 JSONL 压缩文件。</p>
<input id="search" placeholder="搜索题号、题干或问题代码"><select id="source"><option value="">全部来源</option><option>seed</option><option>lawbench</option><option>lexeval</option><option>lecoqa</option><option value="formal">模拟考试（独立库）</option></select><select id="decision"><option value="">全部结论</option><option value="confirmed">已确认具体问题</option><option value="pending">需继续核实</option><option value="revised">已应用修订</option></select><p id="count"></p><button id="prev">上一页</button><button id="next">下一页</button><main id="rows"></main>
<script id="data" type="application/json">DATA</script><script>
const data=JSON.parse(document.getElementById('data').textContent);let page=0;
const search=document.getElementById('search'),source=document.getElementById('source'),decision=document.getElementById('decision'),rows=document.getElementById('rows');
const labels={CONFIRMED_DEFECT:'已确认具体问题',REQUIRES_MORE_VERIFICATION:'需继续核实',NO_SPECIFIC_DEFECT_IDENTIFIED:'本轮未发现具体问题',FOCUSED_CORRECTION_APPLIED:'已应用专项修订',READ_NO_SPECIFIC_DEFECT_IDENTIFIED:'已阅读，本轮未发现具体问题',HISTORICAL_REFERENCE_CONSISTENCY_ONLY:'历史题源一致性核对',SOURCE_CONSISTENT_REQUIRES_LEGAL_REVIEW:'来源一致，法律答案待核实',TEACHING_CANDIDATE_REQUIRES_REWRITE:'教学候选，需重写解析',TEACHING_CANDIDATE_REWRITE:'教学候选，需重写解析',REJECT_CURRENT_PRACTICE_USE:'不适合作为当前日常练习',CHECKED_WITHIN_STATED_SCOPE:'在记录范围内核对',REVISION_PREPARED:'已准备修订，应用情况见归档',SOURCE_LIMITED:'依据访问范围有限'};
function el(tag,text,parent){const node=document.createElement(tag);node.textContent=text;parent.append(node);return node}
function render(){
  const query=search.value.toLowerCase().trim();
  const selected=data.filter(r=>(!source.value||r.sources.includes(source.value))&&(!query||[r.id,r.stem,...r.issues].join(' ').toLowerCase().includes(query))&&(!decision.value||(decision.value==='revised'&&r.appliedRevision)||(decision.value==='confirmed'&&(r.decisions.includes('CONFIRMED_DEFECT')||r.appliedRevision))||(decision.value==='pending'&&r.decisions.some(d=>/REQUIRES|REVIEW|REWRITE|NOT_CLEARED|RISK|SOURCE_LIMITED/.test(d)))));
  const pages=Math.max(1,Math.ceil(selected.length/50));page=Math.min(page,pages-1);rows.replaceChildren();
  document.getElementById('count').textContent=`共 ${selected.length} / ${data.length} 题 · 第 ${page+1} / ${pages} 页`;
  for(const r of selected.slice(page*50,page*50+50)){
    const a=el('article','',rows);el('strong',`#${r.id} · ${r.sources.join(' / ')}`,a);el('p',r.stem,a);
    el('small',r.decisions.map(d=>labels[d]||d).join(' / '),a);el('p',r.methods.join('；'),a);
    el('p',r.issues.join(' · ')||'未列确定缺陷，不代表法律答案已获核验',a);el('p',r.actions.join('；'),a);
    if(r.appliedRevision){
      el('p','已归档并应用修订；以上题干摘录及逐题原始记录对应修订前审核快照。',a);
      const details=el('details','',a);el('summary','查看已应用修订',details);el('pre',JSON.stringify(r.appliedRevision,null,2),details);
    }
    for(const url of r.urls){try{const u=new URL(url);if(!['http:','https:'].includes(u.protocol))continue;const link=el('a','来源 '+u.hostname+' ',a);link.href=u.href;link.target='_blank';link.rel='noopener noreferrer'}catch{}}
    const details=el('details','',a);el('summary','内容指纹',details);el('pre',JSON.stringify({audit:r.contentHash,current:r.currentContentHash},null,2),details);
  }
  document.getElementById('prev').disabled=page===0;document.getElementById('next').disabled=page===pages-1;
}
search.oninput=source.onchange=decision.onchange=()=>{page=0;render()};document.getElementById('prev').onclick=()=>{page--;render()};document.getElementById('next').onclick=()=>{page++;render()};render();
</script></html>'''.replace('DATA', embedded)
    (args.output / 'review.html').write_text(html)
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
