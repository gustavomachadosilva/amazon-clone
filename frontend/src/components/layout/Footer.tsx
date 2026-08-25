import { STORE_NAME } from '../../lib/constants'

const COLUMNS = [
  { title: 'Get to know us', items: ['About the store', 'Careers', 'Sustainability'] },
  { title: 'Sell with us', items: ['Become a partner', 'Affiliate program', 'Advertise products'] },
  { title: 'Payment', items: ['Credit card', 'Store card', 'Gift cards'] },
  { title: 'Help', items: ['Customer service', 'Returns', 'Track a package'] },
]

export default function Footer() {
  return (
    <footer style={{ background: 'var(--color-accent-900)', color: '#f2f2f3', marginTop: 40 }}>
      <div
        style={{
          maxWidth: 1280,
          margin: '0 auto',
          padding: '38px 24px',
          display: 'grid',
          gridTemplateColumns: 'repeat(4, 1fr)',
          gap: 24,
        }}
      >
        {COLUMNS.map((column) => (
          <div key={column.title}>
            <div
              className="h"
              style={{ fontSize: 15, textTransform: 'uppercase', color: '#f2f2f3', marginBottom: 12 }}
            >
              {column.title}
            </div>
            <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 8 }}>
              {column.items.map((item) => (
                <li key={item} style={{ fontSize: 13, color: '#d4d4d7' }}>
                  {item}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div style={{ borderTop: '1px solid var(--color-accent-700)', padding: '16px 24px', textAlign: 'center' }}>
        <span style={{ fontSize: 11.5, color: 'var(--color-accent-400)' }}>
          Academic prototype · {STORE_NAME} · Fictional interface built for coursework
        </span>
      </div>
    </footer>
  )
}
