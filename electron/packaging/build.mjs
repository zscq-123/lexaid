import { packager } from '@electron/packager'
import { execFileSync } from 'node:child_process'
import { fileDigest, stageDesktopApp } from './release-app.mjs'
import { cp, mkdtemp, mkdir, readFile, rename, rm, symlink, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import protocol from '../app-protocol.js'

const electronDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const repo = path.resolve(electronDir, '..')
const pkg = JSON.parse(await readFile(path.join(electronDir, 'package.json')))
const apiUrl = protocol.apiBase(process.env.LEXAID_RELEASE_API_URL || '')
const release = path.resolve(process.env.LEXAID_RELEASE_DIR || path.join(repo, 'release'))
const version = pkg.devDependencies.electron
const zipDir = process.env.LEXAID_ELECTRON_ZIP_DIR
const arches = process.argv.slice(2)
if (!arches.length) arches.push('arm64', 'x64')
if (process.platform !== 'darwin' || arches.some(arch => !['arm64','x64'].includes(arch))) {
  throw new Error('Run on macOS; supported architectures are arm64 and x64.')
}
const run = (bin, args) => execFileSync(bin, args, {stdio:'inherit'})
const checksums = JSON.parse(await readFile(path.join(electronDir, 'node_modules/electron/checksums.json')))
const work = await mkdtemp(path.join(tmpdir(), 'lexaid-package-'))
try {
  const source = path.join(work, 'app')
  await stageDesktopApp(source, electronDir, pkg.version, apiUrl)
  const iconset = path.join(work,'LexAid.iconset')
  const icon = path.join(work,'LexAid.icns')
  run('swift',[path.join(electronDir,'packaging/icon.swift'),iconset])
  run('iconutil',['-c','icns',iconset,'-o',icon])
  await mkdir(release,{recursive:true})
  for (const arch of arches) {
    if (zipDir) {
      const archive = `electron-v${version}-darwin-${arch}.zip`
      if (await fileDigest(path.join(zipDir,archive)) !== checksums[archive]) throw new Error('Electron archive checksum mismatch: ' + archive)
    }
    const [dst] = await packager({
      dir:source, out:path.join(work,'out'), name:'LexAid', appBundleId:'com.lexaid.desktop',
      appCategoryType:'public.app-category.education', appVersion:pkg.version,
      arch, platform:'darwin', electronVersion:version, electronZipDir:zipDir,
      asar:true, icon, prune:false, overwrite:true,
      extendInfo:{CFBundleDisplayName:'LexAid',CFBundleDevelopmentRegion:'zh_CN'}
    })
    const app = path.join(dst,'LexAid.app')
    // The frameworks and helpers in the official runtime already carry nested signatures.
    // Re-sign the customized bundle and verify all nested objects before disk image creation.
    run('codesign',['--force','--deep','--sign','-','--timestamp=none',app])
    run('codesign',['--verify','--deep','--strict',app])
    run('lipo',[path.join(app,'Contents/MacOS/LexAid'),'-verify_arch',arch === 'x64' ? 'x86_64' : 'arm64'])
    const volume = path.join(work,'volume-'+arch)
    await mkdir(volume)
    await rename(app,path.join(volume,'LexAid.app'))
    await symlink('/Applications',path.join(volume,'Applications'))
    const filename = `LexAid-${pkg.version}-macOS-${arch}.dmg`
    const image = path.join(work,filename)
    run('hdiutil',['create','-quiet','-fs','HFS+','-format','UDZO','-volname','LexAid','-srcfolder',volume,image])
    run('hdiutil',['verify','-quiet',image])
    const target = path.join(release,filename)
    const stage = await mkdtemp(path.join(release,'.staging-'))
    try {
      const copy = path.join(stage,filename)
      await cp(image,copy)
      await writeFile(copy+'.sha256',(await fileDigest(copy))+'  '+filename+'\n')
      await rename(copy,target)
      await rename(copy+'.sha256',target+'.sha256')
    } finally {
      await rm(stage,{recursive:true,force:true})
    }
    console.log(target)
  }
} finally {
  await rm(work,{recursive:true,force:true})
}
