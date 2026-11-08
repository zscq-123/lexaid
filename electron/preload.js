const { contextBridge } = require('electron')

contextBridge.exposeInMainWorld('lexaidConfig', {
  apiBaseUrl: location.protocol === 'lexaid:' ? '/api' : (process.env.LEXAID_API_URL || 'http://127.0.0.1:8080/api')
})
