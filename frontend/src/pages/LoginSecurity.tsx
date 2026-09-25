import { Link } from 'react-router-dom'
import { Blueprint } from '../components/ui'
import PasswordForm from '../components/account/PasswordForm'
import ProfileForm from '../components/account/ProfileForm'

export default function LoginSecurity() {
  return (
    <div className="mx-auto max-w-[1320px] px-4 py-4 md:px-6 md:py-6">
      <nav aria-label="Breadcrumb" className="text-[16px] text-paper-700">
        <Link to="/account" className="text-accent-700 hover:underline">
          Your Account
        </Link>{' '}
        <span aria-hidden="true">›</span> <span aria-current="page">Login &amp; security</span>
      </nav>
      <h1 className="mt-2">Login &amp; security</h1>

      <div className="mt-4 grid max-w-[720px] grid-cols-1 gap-4">
        <Blueprint as="section" corners aria-labelledby="profile-heading" className="bg-card p-4 md:p-6">
          <h2 id="profile-heading" className="card-title mb-3">
            Name and email
          </h2>
          <ProfileForm />
        </Blueprint>

        <Blueprint as="section" corners aria-labelledby="password-heading" className="bg-card p-4 md:p-6">
          <h2 id="password-heading" className="card-title mb-3">
            Password
          </h2>
          <PasswordForm />
        </Blueprint>
      </div>
    </div>
  )
}
