import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import type { UserRole } from '../../types/domain'
import RequireAuth from './RequireAuth'

interface RequireRoleProps {
  role: UserRole
  children: ReactNode
}

function RoleGate({ role, children }: RequireRoleProps) {
  const { user } = useAuth()

  if (user?.role !== role) {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}

export default function RequireRole({ role, children }: RequireRoleProps) {
  return (
    <RequireAuth>
      <RoleGate role={role}>{children}</RoleGate>
    </RequireAuth>
  )
}
