import { useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export function useSignOut() {
  const navigate = useNavigate()
  const { signOut } = useAuth()

  // Navigate first and sign out in the same tick: React batches both updates into a single
  // render that already lands on `/` with no user, so protected routes (RequireRole, Checkout,
  // WriteReview) never get to redirect to /signin. Cart and Lists reset themselves on logout.
  return useCallback(() => {
    navigate('/')
    signOut()
  }, [navigate, signOut])
}
