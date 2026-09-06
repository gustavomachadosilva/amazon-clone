import type { HTMLAttributes, TableHTMLAttributes, TdHTMLAttributes, ThHTMLAttributes } from 'react'

export type TableProps = TableHTMLAttributes<HTMLTableElement>

export function Table({ className = '', children, ...props }: TableProps) {
  return (
    <table className={`table ${className}`.trim()} {...props}>
      {children}
    </table>
  )
}

export function TableHead({ className = '', children, ...props }: HTMLAttributes<HTMLTableSectionElement>) {
  return (
    <thead className={className} {...props}>
      {children}
    </thead>
  )
}

export function TableBody({ className = '', children, ...props }: HTMLAttributes<HTMLTableSectionElement>) {
  return (
    <tbody className={className} {...props}>
      {children}
    </tbody>
  )
}

export function TableRow({ className = '', children, ...props }: HTMLAttributes<HTMLTableRowElement>) {
  return (
    <tr className={className} {...props}>
      {children}
    </tr>
  )
}

export function TableHeader({ className = '', children, ...props }: ThHTMLAttributes<HTMLTableCellElement>) {
  return (
    <th className={className} {...props}>
      {children}
    </th>
  )
}

export function TableCell({ className = '', children, ...props }: TdHTMLAttributes<HTMLTableCellElement>) {
  return (
    <td className={className} {...props}>
      {children}
    </td>
  )
}

export default Table

