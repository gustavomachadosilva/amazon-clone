import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { vi } from 'vitest'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { renderWithProviders } from '../test/test-utils'

vi.mock('../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/api')>()
  return {
    ...actual,
    usersApi: {
      me: vi.fn(),
      login: vi.fn(),
      register: vi.fn(),
      updateMe: vi.fn(),
      changePassword: vi.fn(),
    },
    // The real Header loads categories and CartProvider fetches the cart for signed-in users;
    // keep both off the network.
    catalogApi: {
      ...actual.catalogApi,
      getCategories: vi.fn(),
      // List thumbnails (and the Lists page) resolve products by id.
      getById: vi.fn(),
    },
    cartApi: {
      ...actual.cartApi,
      get: vi.fn(),
    },
    // ListsProvider fetches the signed-in user's lists; the Your Lists section reads them.
    listsApi: {
      ...actual.listsApi,
      listMine: vi.fn(),
      create: vi.fn(),
    },
  }
})

// Imported after the mock so they pick up the mocked module.
import {
  ApiRequestError,
  cartApi,
  catalogApi,
  listsApi,
  usersApi,
  type Product,
  type UserProfile,
  type WishListView,
} from '../services/api'
import { AUTH_STORAGE_KEY } from '../services/auth-token'
import RequireAuth from '../components/auth/RequireAuth'
import Header from '../components/layout/Header'
import { ListsProvider } from '../context/ListsContext'
import type { UserRole } from '../types/domain'
import Account from './Account'
import Lists from './Lists'
import SignIn from './SignIn'

const mockedUsersApi = vi.mocked(usersApi)
const mockedCartApi = vi.mocked(cartApi)
const mockedListsApi = vi.mocked(listsApi)
const mockedCatalogApi = vi.mocked(catalogApi)

function productFor(id: number): Product {
  return {
    id,
    name: `Product ${id}`,
    description: '',
    price: 10,
    stockQuantity: 5,
    category: 'Books',
    sellerId: 2,
    imageUrl: `https://img.example/${id}.png`,
    brand: null,
    warrantyMonths: null,
    modelNumber: null,
    listPrice: null,
    averageRating: 0,
    reviewCount: 0,
  }
}

function listFor(id: number, name: string, productIds: number[]): WishListView {
  return { id, buyerId: 1, name, productIds, createdAt: '2026-09-01T12:00:00Z' }
}

function profileFor(role: UserRole): UserProfile {
  return {
    id: 1,
    name: role === 'SELLER' ? 'Test Seller' : 'Test Buyer',
    email: role === 'SELLER' ? 'seller@example.com' : 'buyer@example.com',
    role,
    createdAt: '2026-03-15T12:00:00Z',
  }
}

function seedAuth(role: UserRole) {
  const { id, name, email } = profileFor(role)
  localStorage.setItem(
    AUTH_STORAGE_KEY,
    JSON.stringify({
      id,
      name,
      email,
      role,
      token: 'test-token',
      tokenExpiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    }),
  )
}

function storedUser() {
  return JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY) ?? 'null')
}

// Exposes the current URL so tests can check where a redirect landed.
function LocationProbe() {
  const { pathname, hash } = useLocation()
  return <div data-testid="location">{pathname + hash}</div>
}

// The real Header is rendered so tests can check the "Hello, {name}" greeting; the
// /account/security redirect mirrors App.tsx.
function renderAccount(route = '/account') {
  return renderWithProviders(
    <ListsProvider>
      <Header />
      <Routes>
        <Route
          path="/account"
          element={
            <RequireAuth>
              <Account />
            </RequireAuth>
          }
        />
        <Route path="/account/security" element={<Navigate to="/account#account-info" replace />} />
        <Route path="/signin" element={<SignIn />} />
        <Route path="/" element={<div>Home stub</div>} />
        <Route path="/lists" element={<Lists />} />
      </Routes>
      <LocationProbe />
    </ListsProvider>,
    { route },
  )
}

// Renders the page for a signed-in buyer and waits for the profile (and the Header's
// categories) to settle.
async function renderLoadedAccount(route = '/account') {
  seedAuth('BUYER')
  mockedUsersApi.me.mockResolvedValue(profileFor('BUYER'))
  renderAccount(route)
  await profileCard().findByText('Test Buyer')
  await act(async () => {})
}

function profileCard() {
  return within(screen.getByRole('region', { name: 'Profile' }))
}

function accountInfo() {
  return within(screen.getByRole('region', { name: 'Your account information' }))
}

function fillPassword(current: string, next: string) {
  fireEvent.change(accountInfo().getByLabelText('Current password'), { target: { value: current } })
  fireEvent.change(accountInfo().getByLabelText('New password'), { target: { value: next } })
  fireEvent.change(accountInfo().getByLabelText('Confirm new password'), { target: { value: next } })
  fireEvent.click(accountInfo().getByRole('button', { name: 'Save password' }))
}

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
  mockedCatalogApi.getCategories.mockResolvedValue([])
  mockedCartApi.get.mockResolvedValue({ userId: 1, items: [], savedForLater: [], itemCount: 0, total: 0 })
  mockedListsApi.listMine.mockResolvedValue([])
  mockedCatalogApi.getById.mockImplementation(async (id: number) => productFor(id))
  // jsdom has no layout, so it doesn't implement scrollIntoView.
  Element.prototype.scrollIntoView = vi.fn()
})

describe('Account page', () => {
  it('shows a loading state, then the buyer profile', async () => {
    seedAuth('BUYER')
    mockedUsersApi.me.mockResolvedValue(profileFor('BUYER'))

    renderAccount()

    expect(screen.getByRole('heading', { name: 'Your Account' })).toBeInTheDocument()
    const profile = screen.getByRole('region', { name: 'Profile' })
    expect(within(profile).getByRole('status')).toHaveTextContent('Loading your account')
    // Sign out lives in the profile card in every state, including while it loads.
    expect(within(profile).getByRole('button', { name: 'Sign out' })).toBeInTheDocument()

    expect(await within(profile).findByText('Test Buyer')).toBeInTheDocument()
    expect(within(profile).getByText('buyer@example.com')).toBeInTheDocument()
    expect(within(profile).getByText('Buyer')).toBeInTheDocument()
    expect(within(profile).getByText('Member since March 2026')).toBeInTheDocument()
    expect(within(profile).queryByRole('status')).not.toBeInTheDocument()
  })

  it('shows an error when the profile fails to load and retries on demand', async () => {
    seedAuth('BUYER')
    mockedUsersApi.me.mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce(profileFor('BUYER'))

    renderAccount()
    const profile = screen.getByRole('region', { name: 'Profile' })

    expect(await within(profile).findByRole('alert')).toHaveTextContent("We couldn't load your account details.")
    expect(within(profile).getByRole('button', { name: 'Sign out' })).toBeInTheDocument()
    fireEvent.click(within(profile).getByRole('button', { name: 'Try again' }))

    expect(await within(profile).findByText('Test Buyer')).toBeInTheDocument()
    expect(mockedUsersApi.me).toHaveBeenCalledTimes(2)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('labels buyers with a neutral solid role label', async () => {
    seedAuth('BUYER')
    mockedUsersApi.me.mockResolvedValue(profileFor('BUYER'))

    renderAccount()
    const profile = screen.getByRole('region', { name: 'Profile' })
    await within(profile).findByText('Test Buyer')

    expect(within(profile).getByText('Buyer')).toHaveClass('bg-paper-200')
    expect(within(profile).queryByText('Seller')).not.toBeInTheDocument()
  })

  it('labels sellers with an accent role label', async () => {
    seedAuth('SELLER')
    mockedUsersApi.me.mockResolvedValue(profileFor('SELLER'))

    renderAccount()
    const profile = screen.getByRole('region', { name: 'Profile' })
    await within(profile).findByText('Test Seller')

    expect(within(profile).getByText('Seller')).toHaveClass('bg-accent-600')
    expect(within(profile).queryByText('Buyer')).not.toBeInTheDocument()
  })

  it('offers buyer shortcuts without Seller Central', async () => {
    await renderLoadedAccount()

    const shortcuts = screen.getByRole('list', { name: 'Account shortcuts' })
    expect(within(shortcuts).getAllByRole('listitem')).toHaveLength(1)
    expect(within(shortcuts).getByRole('link', { name: /Your Orders/ })).toHaveAttribute('href', '/orders')
    // Lists have their own section now, so there's no generic "Your Lists" shortcut.
    expect(within(shortcuts).queryByText('Your Lists')).not.toBeInTheDocument()
    // Name, email and password are edited on the page itself now.
    expect(within(shortcuts).queryByText('Login & security')).not.toBeInTheDocument()
    expect(within(shortcuts).queryByText('Seller Central')).not.toBeInTheDocument()
    expect(within(shortcuts).getByRole('heading', { level: 3, name: 'Your Orders' })).toBeInTheDocument()
  })

  it('shows the Seller badge and Seller Central shortcut to sellers', async () => {
    seedAuth('SELLER')
    mockedUsersApi.me.mockResolvedValue(profileFor('SELLER'))

    renderAccount()

    expect(await profileCard().findByText('Seller')).toBeInTheDocument()
    const shortcuts = screen.getByRole('list', { name: 'Account shortcuts' })
    expect(within(shortcuts).getAllByRole('listitem')).toHaveLength(2)
    expect(within(shortcuts).queryByText('Login & security')).not.toBeInTheDocument()
    expect(within(shortcuts).getByRole('link', { name: /Seller Central/ })).toHaveAttribute('href', '/seller')
    expect(within(shortcuts).getByRole('heading', { level: 3, name: 'Seller Central' })).toBeInTheDocument()
  })

  it('shows the account information and lists sections', async () => {
    await renderLoadedAccount()

    expect(screen.getByRole('heading', { level: 2, name: 'Your account information' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: 'Your Lists' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Your Lists' })).toBeInTheDocument()
    expect(accountInfo().getByText('Test Buyer')).toBeInTheDocument()
    expect(accountInfo().getByText('buyer@example.com')).toBeInTheDocument()
    expect(accountInfo().getByRole('button', { name: 'Edit name and email' })).toBeInTheDocument()
    expect(accountInfo().getByRole('button', { name: 'Change password' })).toBeInTheDocument()
  })

  it('signs out, goes home and clears the stored session', async () => {
    await renderLoadedAccount()

    fireEvent.click(profileCard().getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByText('Home stub')).toBeInTheDocument()
    expect(JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY) ?? 'undefined')).toBeNull()
  })

  it('sends signed-out visitors to sign in and brings them back afterwards', async () => {
    mockedUsersApi.login.mockResolvedValue({
      ...profileFor('BUYER'),
      token: 'test-token',
      expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    })
    mockedUsersApi.me.mockResolvedValue(profileFor('BUYER'))

    renderAccount()

    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(mockedUsersApi.me).not.toHaveBeenCalled()

    const form = screen.getByRole('button', { name: 'Continue' }).closest('form')!
    fireEvent.change(within(form).getByLabelText('Email'), { target: { value: 'buyer@example.com' } })
    fireEvent.change(within(form).getByLabelText('Password'), { target: { value: 'password123' } })
    fireEvent.click(within(form).getByText('Continue'))

    expect(await screen.findByRole('heading', { name: 'Your Account' })).toBeInTheDocument()
    expect(await profileCard().findByText('Test Buyer')).toBeInTheDocument()
  })

  describe('editing account information', () => {
    it('opens the name and email form in place and Cancel discards the changes', async () => {
      await renderLoadedAccount()

      fireEvent.click(accountInfo().getByRole('button', { name: 'Edit name and email' }))
      expect(accountInfo().getByLabelText('Name')).toHaveFocus()
      expect(accountInfo().getByLabelText('Name')).toHaveValue('Test Buyer')
      expect(accountInfo().getByLabelText('Email')).toHaveValue('buyer@example.com')

      fireEvent.change(accountInfo().getByLabelText('Name'), { target: { value: 'Draft Name' } })
      fireEvent.click(accountInfo().getByRole('button', { name: 'Cancel' }))

      expect(accountInfo().queryByLabelText('Name')).not.toBeInTheDocument()
      expect(accountInfo().getByText('Test Buyer')).toBeInTheDocument()
      expect(accountInfo().getByRole('button', { name: 'Edit name and email' })).toHaveFocus()
      expect(mockedUsersApi.updateMe).not.toHaveBeenCalled()

      // Reopening starts again from the saved values.
      fireEvent.click(accountInfo().getByRole('button', { name: 'Edit name and email' }))
      expect(accountInfo().getByLabelText('Name')).toHaveValue('Test Buyer')
    })

    it('saves the name and email and updates the profile card and header right away', async () => {
      mockedUsersApi.updateMe.mockResolvedValue({ ...profileFor('BUYER'), name: 'New Name', email: 'new@example.com' })
      await renderLoadedAccount()
      expect(screen.getByText('Hello, Test Buyer')).toBeInTheDocument()

      fireEvent.click(accountInfo().getByRole('button', { name: 'Edit name and email' }))
      fireEvent.change(accountInfo().getByLabelText('Name'), { target: { value: 'New Name' } })
      fireEvent.change(accountInfo().getByLabelText('Email'), { target: { value: 'new@example.com' } })
      fireEvent.click(accountInfo().getByRole('button', { name: 'Save' }))

      expect(await accountInfo().findByRole('status')).toHaveTextContent('Your name and email have been updated.')
      expect(mockedUsersApi.updateMe).toHaveBeenCalledWith({ name: 'New Name', email: 'new@example.com' })
      expect(accountInfo().queryByLabelText('Name')).not.toBeInTheDocument()
      expect(accountInfo().getByText('New Name')).toBeInTheDocument()
      expect(accountInfo().getByText('new@example.com')).toBeInTheDocument()
      expect(profileCard().getByText('New Name')).toBeInTheDocument()
      expect(profileCard().getByText('new@example.com')).toBeInTheDocument()
      expect(screen.getByText('Hello, New Name')).toBeInTheDocument()
      expect(accountInfo().getByRole('button', { name: 'Edit name and email' })).toHaveFocus()
      await waitFor(() =>
        expect(storedUser()).toMatchObject({ name: 'New Name', email: 'new@example.com', token: 'test-token' }),
      )
    })

    it('keeps the form open with the error when the email belongs to another account', async () => {
      mockedUsersApi.updateMe.mockRejectedValue(new ApiRequestError(409, 'Email already registered'))
      await renderLoadedAccount()

      fireEvent.click(accountInfo().getByRole('button', { name: 'Edit name and email' }))
      fireEvent.change(accountInfo().getByLabelText('Email'), { target: { value: 'taken@example.com' } })
      fireEvent.click(accountInfo().getByRole('button', { name: 'Save' }))

      expect(await accountInfo().findByText('That email is already in use by another account.')).toBeInTheDocument()
      expect(accountInfo().getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true')
      expect(profileCard().getByText('buyer@example.com')).toBeInTheDocument()
      expect(screen.getByText('Hello, Test Buyer')).toBeInTheDocument()
      expect(storedUser()).toMatchObject({ name: 'Test Buyer', email: 'buyer@example.com' })
    })

    it('changes the password in place and keeps the user signed in', async () => {
      mockedUsersApi.changePassword.mockResolvedValue(undefined)
      await renderLoadedAccount()

      fireEvent.click(accountInfo().getByRole('button', { name: 'Change password' }))
      expect(accountInfo().getByLabelText('Current password')).toHaveFocus()

      fillPassword('oldpassword', 'newpassword123')

      expect(await accountInfo().findByRole('status')).toHaveTextContent('Your password has been changed.')
      expect(mockedUsersApi.changePassword).toHaveBeenCalledWith({
        currentPassword: 'oldpassword',
        newPassword: 'newpassword123',
      })
      expect(accountInfo().queryByLabelText('Current password')).not.toBeInTheDocument()
      expect(accountInfo().getByRole('button', { name: 'Change password' })).toHaveFocus()
      expect(screen.getByText('Hello, Test Buyer')).toBeInTheDocument()
      expect(storedUser()).toMatchObject({ token: 'test-token' })
    })

    it('keeps the password form open when the current password is wrong', async () => {
      mockedUsersApi.changePassword.mockRejectedValue(new ApiRequestError(400, 'Senha atual incorreta'))
      await renderLoadedAccount()

      fireEvent.click(accountInfo().getByRole('button', { name: 'Change password' }))
      fillPassword('wrongpassword', 'newpassword123')

      expect(await accountInfo().findByText('Your current password is incorrect.')).toBeInTheDocument()
      expect(accountInfo().getByLabelText('Current password')).toHaveAttribute('aria-invalid', 'true')
      expect(accountInfo().queryByText('Your password has been changed.')).not.toBeInTheDocument()
    })

    it('opens only one form at a time', async () => {
      await renderLoadedAccount()

      fireEvent.click(accountInfo().getByRole('button', { name: 'Edit name and email' }))
      expect(accountInfo().getByLabelText('Name')).toBeInTheDocument()

      fireEvent.click(accountInfo().getByRole('button', { name: 'Change password' }))
      expect(accountInfo().getByLabelText('Current password')).toHaveFocus()
      expect(accountInfo().queryByLabelText('Name')).not.toBeInTheDocument()
      expect(accountInfo().getByRole('button', { name: 'Edit name and email' })).toBeInTheDocument()
    })

    it('redirects the old Login & security page to the account information section', async () => {
      await renderLoadedAccount('/account/security')

      expect(screen.getByRole('heading', { level: 1, name: 'Your Account' })).toBeInTheDocument()
      expect(screen.getByTestId('location')).toHaveTextContent('/account#account-info')
      const section = screen.getByRole('region', { name: 'Your account information' })
      expect(section).toHaveAttribute('id', 'account-info')
      expect(vi.mocked(Element.prototype.scrollIntoView).mock.contexts).toContain(section)
    })

    it('scrolls to the account information section when linked directly', async () => {
      await renderLoadedAccount('/account#account-info')

      const section = screen.getByRole('region', { name: 'Your account information' })
      expect(vi.mocked(Element.prototype.scrollIntoView).mock.contexts).toContain(section)
    })
  })
})

describe('Account page — Your Lists section', () => {
  function renderSignedIn() {
    seedAuth('BUYER')
    mockedUsersApi.me.mockResolvedValue(profileFor('BUYER'))
    renderAccount()
    return screen.getByRole('region', { name: 'Your Lists' })
  }

  it('shows a loading state inside the section while lists load', async () => {
    let resolveLists: (lists: WishListView[]) => void = () => {}
    mockedListsApi.listMine.mockReturnValue(new Promise((resolve) => (resolveLists = resolve)))

    const section = renderSignedIn()

    expect(within(section).getByRole('status')).toHaveTextContent('Loading your lists')
    // No empty state flashes while the first fetch is still in flight.
    expect(within(section).queryByText(/haven't created any lists/)).not.toBeInTheDocument()

    resolveLists([listFor(1, 'Wishlist', [])])
    expect(await within(section).findByRole('link', { name: 'Wishlist, 0 items' })).toBeInTheDocument()
    expect(within(section).queryByRole('status')).not.toBeInTheDocument()
  })

  it('shows up to four lists with name, item count, thumbnails and a link to each', async () => {
    mockedListsApi.listMine.mockResolvedValue([
      listFor(5, 'Birthday ideas', [1, 2, 3, 4]),
      listFor(4, 'Books', [2]),
      listFor(3, 'Empty one', []),
      listFor(2, 'Kitchen', [5, 6]),
      listFor(1, 'Oldest list', [7]),
    ])

    const section = renderSignedIn()
    const grid = await within(section).findByRole('list', { name: 'Your lists' })

    expect(within(grid).getAllByRole('listitem')).toHaveLength(4)
    expect(within(section).queryByText('Oldest list')).not.toBeInTheDocument()

    const birthday = within(section).getByRole('link', { name: 'Birthday ideas, 4 items' })
    expect(birthday).toHaveAttribute('href', '/lists?list=5')
    expect(within(birthday).getByRole('heading', { level: 3, name: 'Birthday ideas' })).toBeInTheDocument()
    expect(within(birthday).getByText('4 items')).toBeInTheDocument()
    // At most three thumbnails, from the first products of the list.
    await waitFor(() => expect(within(birthday).getAllByRole('img')).toHaveLength(3))
    expect(within(birthday).getByAltText('Product 1')).toHaveAttribute('src', 'https://img.example/1.png')
    expect(within(birthday).queryByAltText('Product 4')).not.toBeInTheDocument()

    const books = within(section).getByRole('link', { name: 'Books, 1 item' })
    expect(books).toHaveAttribute('href', '/lists?list=4')
    expect(within(books).getByText('1 item')).toBeInTheDocument()

    const empty = within(section).getByRole('link', { name: 'Empty one, 0 items' })
    expect(within(empty).getByText('No items yet')).toBeInTheDocument()

    expect(within(section).getByRole('link', { name: 'Kitchen, 2 items' })).toHaveAttribute('href', '/lists?list=2')
    expect(within(section).getByRole('link', { name: 'See all lists' })).toHaveAttribute('href', '/lists')

    // One batched fetch per distinct thumbnail product (product 2 is in two lists; 7 isn't shown).
    expect(mockedCatalogApi.getById).toHaveBeenCalledTimes(5)
  })

  it('opens the Lists page with the clicked list selected', async () => {
    mockedListsApi.listMine.mockResolvedValue([listFor(2, 'Newest', [1]), listFor(1, 'Gifts', [3])])

    const section = renderSignedIn()
    fireEvent.click(await within(section).findByRole('link', { name: 'Gifts, 1 item' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Gifts' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Gifts/ })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: /Newest/ })).toHaveAttribute('aria-pressed', 'false')
  })

  it('lets a user with no lists create one from the empty state', async () => {
    mockedListsApi.create.mockResolvedValue(listFor(9, 'Birthday ideas', []))

    const section = renderSignedIn()
    expect(await within(section).findByText("You haven't created any lists yet.")).toBeInTheDocument()
    expect(within(section).queryByRole('link', { name: 'See all lists' })).not.toBeInTheDocument()

    const input = within(section).getByRole('textbox', { name: 'New list name' })
    const submit = within(section).getByRole('button', { name: 'Create list' })

    fireEvent.change(input, { target: { value: '   ' } })
    fireEvent.click(submit)
    expect(mockedListsApi.create).not.toHaveBeenCalled()

    fireEvent.change(input, { target: { value: '  Birthday ideas  ' } })
    fireEvent.click(submit)

    expect(await within(section).findByRole('link', { name: 'Birthday ideas, 0 items' })).toHaveAttribute(
      'href',
      '/lists?list=9',
    )
    expect(mockedListsApi.create).toHaveBeenCalledWith('Birthday ideas')
    expect(within(section).queryByText("You haven't created any lists yet.")).not.toBeInTheDocument()
  })

  it('shows an inline error when creating the first list fails', async () => {
    mockedListsApi.create.mockRejectedValue(new Error('boom'))

    const section = renderSignedIn()
    fireEvent.change(await within(section).findByRole('textbox', { name: 'New list name' }), {
      target: { value: 'Gifts' },
    })
    fireEvent.click(within(section).getByRole('button', { name: 'Create list' }))

    expect(await within(section).findByRole('alert')).toHaveTextContent('Could not create the list. Please try again.')
    expect(within(section).getByRole('button', { name: 'Create list' })).toBeEnabled()
  })

  it('shows an error with a retry when the lists fail to load', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    mockedListsApi.listMine.mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce([listFor(1, 'Gifts', [])])

    const section = renderSignedIn()

    expect(await within(section).findByRole('alert')).toHaveTextContent("We couldn't load your lists.")
    fireEvent.click(within(section).getByRole('button', { name: 'Try again' }))

    expect(await within(section).findByRole('link', { name: 'Gifts, 0 items' })).toBeInTheDocument()
    expect(mockedListsApi.listMine).toHaveBeenCalledTimes(2)
    expect(within(section).queryByRole('alert')).not.toBeInTheDocument()
    consoleError.mockRestore()
  })
})
