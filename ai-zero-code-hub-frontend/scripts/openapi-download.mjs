import fs from 'node:fs/promises'
import process from 'node:process'

const url = process.env.OPENAPI_URL ?? 'http://localhost:8123/api/v3/api-docs'
const outFile = process.env.OPENAPI_OUT ?? 'openapi.json'

const res = await fetch(url, {
  headers: {
    Accept: 'application/json',
  },
})

if (!res.ok) {
  throw new Error(`Failed to download OpenAPI: ${res.status} ${res.statusText} (${url})`)
}

const text = await res.text()
await fs.writeFile(outFile, text, 'utf8')

console.log(`OpenAPI downloaded: ${outFile}`)
