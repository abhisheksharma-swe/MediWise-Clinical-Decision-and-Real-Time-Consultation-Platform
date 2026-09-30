import React from 'react'
import { AlertTriangleIcon, RefreshCwIcon } from './Icons'

/**
 * Top-level render-error safety net. A React error boundary must be a class
 * component (there's no hook equivalent for getDerivedStateFromError/
 * componentDidCatch yet) — without this, any uncaught render error white-screens
 * the whole admin panel with no fallback UI and no visibility into what happened.
 */
export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props)
    this.state = { hasError: false }
  }

  static getDerivedStateFromError() {
    return { hasError: true }
  }

  componentDidCatch(error, errorInfo) {
    // eslint-disable-next-line no-console
    console.error('Uncaught render error in MediWise Admin:', error, errorInfo)
  }

  handleReload = () => {
    window.location.reload()
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="error-state" style={{ margin: '48px auto', maxWidth: 480 }}>
          <div className="error-state-icon-container">
            <AlertTriangleIcon size={32} />
          </div>
          <h3 className="error-state-title">Something went wrong</h3>
          <p className="error-state-msg">
            An unexpected error occurred in the admin panel. Reloading the page usually resolves it.
          </p>
          <button className="btn btn-primary btn-retry" onClick={this.handleReload}>
            <RefreshCwIcon size={16} />
            <span>Reload</span>
          </button>
        </div>
      )
    }

    return this.props.children
  }
}
