import { packager } from '@electron/packager'
import { execFileSync } from 'node:child_process'
import { mkdtemp, mkdir, readFile, rename, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import protocol from '../../app-protocol.js'
import { fileDigest, stageDesktopApp } from '../release-app.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const electronDir = path.resolve(here, '../..')
const release = path.resolve(process.env.LEXAID_RELEASE_DIR || path.join(electronDir, '../release'))
const apiUrl = protocol.apiBase(process.env.LEXAID_RELEASE_API_URL || '')
const pkg = JSON.parse(await readFile(path.join(electronDir, 'package.json')))
const version = pkg.devDependencies.electron
const zipDir = process.env.LEXAID_ELECTRON_ZIP_DIR
const work = await mkdtemp(path.join(tmpdir(), 'lexaid-win-'))
try {
  if (zipDir) {
    const archive = `electron-v${version}-win32-x64.zip`
    const checksums = JSON.parse(await readFile(path.join(electronDir, 'node_modules/electron/checksums.json')))
    if (await fileDigest(path.join(zipDir, archive)) !== checksums[archive]) {
      throw new Error('Electron archive checksum mismatch: ' + archive)
    }
  }
  const source = path.join(work, 'source')
  await stageDesktopApp(source, electronDir, pkg.version, apiUrl)
  const icon = path.join(here, 'LexAid.ico')
  const [payload] = await packager({
    dir: source, out: path.join(work, 'out'), name: 'LexAid', appVersion: pkg.version,
    arch: 'x64', platform: 'win32', electronVersion: version, electronZipDir: zipDir,
    asar: true, icon, prune: false, overwrite: true, win32metadata: { CompanyName: 'LexAid' }
  })
  await mkdir(release, { recursive: true })
  const stage = await mkdtemp(path.join(release, '.staging-win-'))
  try {
    const filename = `LexAid-${pkg.version}-Windows-x64-Setup.exe`
    const image = path.join(stage, filename)
    const flag = process.platform === 'win32' ? '/' : '-'
    execFileSync('makensis', [
      flag + 'V2', flag + 'WX', flag + 'DVERSION=' + pkg.version,
      flag + 'DOUTFILE=' + image, flag + 'DPAYLOAD=' + payload,
      flag + 'DICONFILE=' + icon, path.join(here, 'installer.nsi')
    ], { stdio: 'inherit' })
    await writeFile(image + '.sha256', (await fileDigest(image)) + '  ' + filename + '\n')
    await rename(image, path.join(release, filename))
    await rename(image + '.sha256', path.join(release, filename + '.sha256'))
    console.log(path.join(release, filename))
  } finally {
    await rm(stage, { recursive: true, force: true })
  }
} finally {
  await rm(work, { recursive: true, force: true })
}
