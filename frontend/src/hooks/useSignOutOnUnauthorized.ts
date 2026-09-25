import { useEffect, useRef } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { onUnauthorized } from '../services/auth-events'

// Ends the session when the API rejects the stored token (expired or revoked while the page
// was open) and sends the user to /signin, remembering where they were so they return there.
export function useSignOutOnUnauthorized() {
  const navigate = useNavigate()
  const location = useLocation()
  const { signOut } = useAuth()

  const locationRef = useRef(location)
  useEffect(() => {
    locationRef.current = location
  }, [location])

  // Several requests usually fail together with the same token; only react to the first.
  const handledTokenRef = useRef<string | null>(null)

  useEffect(
    () =>
      onUnauthorized((token) => {
        if (token === handledTokenRef.current) return
        handledTokenRef.current = token

        // Same pattern as useSignOut: navigate and sign out in the same tick so protected
        // routes don't get a render in between to do their own redirect.
        if (locationRef.current.pathname !== '/signin') {
          navigate('/signin', { replace: true, state: { from: locationRef.current } })
        }
        signOut()
      }),
    [navigate, signOut],
  )
}
