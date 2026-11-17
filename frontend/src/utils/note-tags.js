export function noteTags(content) {
  return [...new Set([...String(content || '').matchAll(/(?:^|\s)#([\p{L}\p{N}_-]{1,30})/gu)].map(match => match[1]))]
}
