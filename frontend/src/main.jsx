import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './auth/AuthContext'
import './styles.css'

// Start the entrance animations only once the fonts are in (or after 1.5 s on a slow connection).
const ready = () => document.documentElement.classList.add('fonts-ready')
if (document.fonts?.load) {
  Promise.all(['400 1em Fraunces', 'italic 400 1em Fraunces', '400 1em Inter'].map((f) => document.fonts.load(f)))
    .then(ready, ready)
  setTimeout(ready, 1500)
} else {
  ready()
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
