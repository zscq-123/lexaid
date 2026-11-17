function cleanText(value) {
  return String(value ?? '')
    .replace(/\r\n?/g, '\n')
    .replace(/[ \t]+\n/g, '\n')
    .trim()
}

function canonicalText(value) {
  return cleanText(value).replace(/\s+/g, ' ')
}

function answerLetters(rawAnswer, optionKeys) {
  const raw = cleanText(rawAnswer).toUpperCase()
  const patterns = [
    /(?:正确答案|答案)[^A-H]*([A-H]+)/,
    /^([A-H]+)[。.!！]?$/
  ]
  for (const pattern of patterns) {
    const match = raw.match(pattern)
    if (!match) continue
    const letters = [...new Set([...match[1]])]
    if (letters.length > 0 && letters.every((letter) => optionKeys.includes(letter))) {
      return letters
    }
  }
  return []
}

export function parseChoice(question, rawAnswer) {
  const text = cleanText(question)
  const markers = [...text.matchAll(/([A-H])[:：]/g)]
  let best = []
  for (let start = 0; start < markers.length; start++) {
    if (markers[start][1] !== 'A') continue
    const run = [markers[start]]
    let expected = 'B'.charCodeAt(0)
    for (let i = start + 1; i < markers.length; i++) {
      if (markers[i][1].charCodeAt(0) === expected) {
        run.push(markers[i])
        expected++
      } else if (markers[i][1] === 'A') {
        break
      }
    }
    if (run.length > best.length) best = run
  }
  if (best.length < 2) return null

  const options = best.map((marker, index) => {
    const valueStart = marker.index + marker[0].length
    const valueEnd = index + 1 < best.length ? best[index + 1].index : text.length
    return { key: marker[1], text: cleanText(text.slice(valueStart, valueEnd)) }
  })
  if (options.some((option) => !option.text)) return null

  const letters = answerLetters(rawAnswer, options.map((option) => option.key))
  if (letters.length === 0) return null
  return {
    type: letters.length === 1 ? 'SINGLE' : 'MULTIPLE',
    stem: cleanText(text.slice(0, best[0].index)).replace(/[：:]$/, '').trim(),
    options,
    answer: letters
  }
}

export function toQuestion({ question, answer, instruction, category, difficulty }) {
  const choice = parseChoice(question, answer)
  if (choice) {
    return {
      ...choice,
      analysis: cleanText(instruction),
      difficulty,
      category
    }
  }
  return {
    type: 'SHORT',
    stem: cleanText(question),
    options: null,
    answer: cleanText(answer),
    analysis: cleanText(instruction),
    difficulty,
    category
  }
}

