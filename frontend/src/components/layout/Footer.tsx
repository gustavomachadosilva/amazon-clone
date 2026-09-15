import { Link } from 'react-router-dom'
import { STORE_NAME } from '../../lib/constants'

const COLUMNS: { title: string; items: { label: string; to?: string }[] }[] = [
  { title: 'Get to know us', items: [{ label: 'About the store' }, { label: 'Careers' }, { label: 'Sustainability' }] },
  {
    title: 'Sell with us',
    items: [{ label: 'Become a partner' }, { label: 'Affiliate program' }, { label: 'Advertise products' }],
  },
  { title: 'Payment', items: [{ label: 'Credit card' }, { label: 'Store card' }, { label: 'Gift cards' }] },
  {
    title: 'Help',
    items: [
      { label: 'Customer service' },
      { label: 'Returns', to: '/orders' },
      { label: 'Track a package', to: '/orders' },
    ],
  },
]

export default function Footer() {
  return (
    <footer className="mt-10 bg-accent-900 text-[#f2f2f3]">
      <div className="mx-auto grid max-w-[1280px] grid-cols-2 gap-6 px-4 py-8 sm:grid-cols-4 md:px-6 md:py-[38px]">
        {COLUMNS.map((column) => (
          <div key={column.title}>
            <div className="h mb-3 text-[15px] uppercase text-[#f2f2f3]">{column.title}</div>
            <ul className="m-0 flex list-none flex-col gap-2 p-0">
              {column.items.map((item) => (
                <li key={item.label} className="text-[13px] text-neutral-300">
                  {item.to ? (
                    <Link to={item.to} className="text-neutral-300 hover:text-[#f2f2f3] hover:underline">
                      {item.label}
                    </Link>
                  ) : (
                    item.label
                  )}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div className="border-t border-accent-700 px-4 py-4 text-center">
        <span className="text-[11.5px] text-accent-400">
          Academic prototype · {STORE_NAME} · Fictional interface built for coursework
        </span>
      </div>
    </footer>
  )
}
