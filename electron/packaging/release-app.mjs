import { createHash } from 'node:crypto'
import { createReadStream } from 'node:fs'
import { cp, mkdir, readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'

export async function fileDigest(file) {
  const hash = createHash('sha256')
  for await (const chunk of createReadStream(file)) hash.update(chunk)
  return hash.digest('hex')
}

export async function stageDesktopApp(source, electronDir, version, apiUrl) {
  const dist = path.resolve(electronDir, '../frontend/dist')
  await readFile(path.join(dist, 'index.html'))
  await mkdir(source)
  for (const file of ['main.js', 'preload.js', 'paper-link.js', 'app-protocol.js', 'renderer-server.js']) {
    await cp(path.join(electronDir, file), path.join(source, file))
  }
  await writeFile(path.join(source, 'package.json'), JSON.stringify({
    name: 'lexaid', productName: 'LexAid', version, main: 'main.js'
  }))
  await writeFile(path.join(source, 'release-config.json'), JSON.stringify({ apiUrl }))
  await cp(dist, path.join(source, 'dist'), { recursive: true })
}
