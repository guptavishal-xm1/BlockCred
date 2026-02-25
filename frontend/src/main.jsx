import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'
import PublicVerifyPage from './components/PublicVerifyPage.jsx'

const path = window.location.pathname
const RootComponent = path === '/verify' ? PublicVerifyPage : App

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <RootComponent />
  </StrictMode>,
)
