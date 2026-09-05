import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import type { UserRole } from '../../types/domain'

interface RequireRoleProps {
  role: UserRole
  children: ReactNode
}

export default function RequireRole({ role, children }: RequireRoleProps) {
  const { user } = useAuth()
  const location = useLocation()

  if (!user) {
    return <Navigate to="/signin" replace state={{ from: location }} />
  }

  if (user.role !== role) {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}
