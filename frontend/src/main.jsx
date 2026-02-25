import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'
import PublicVerifyPage from './components/PublicVerifyPage.jsx'
import LoginPage from './components/LoginPage.jsx'
import OpsPage from './components/OpsPage.jsx'

const path = window.location.pathname
let RootComponent = LoginPage
if (path === '/verify') {
  RootComponent = PublicVerifyPage
} else if (path === '/login' || path === '/') {
  RootComponent = LoginPage
} else if (path === '/issuer') {
  RootComponent = App
} else if (path === '/ops') {
  RootComponent = OpsPage
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <RootComponent />
  </StrictMode>,
)
