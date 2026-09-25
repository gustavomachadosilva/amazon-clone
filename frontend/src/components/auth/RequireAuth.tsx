import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'

interface RequireAuthProps {
  children: ReactNode
}

// Sends signed-out visitors to /signin, remembering where they were so SignIn can bring them back.
export default function RequireAuth({ children }: RequireAuthProps) {
  const { user } = useAuth()
  const location = useLocation()

  if (!user) {
    return <Navigate to="/signin" replace state={{ from: location }} />
  }

  return <>{children}</>
}
