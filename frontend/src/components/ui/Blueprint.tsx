import type { ElementType, HTMLAttributes, ReactNode } from 'react'

interface BlueprintProps extends HTMLAttributes<HTMLElement> {
  as?: ElementType
  // Draws the Industry "+" registration marks on the four corners.
  corners?: boolean
  children: ReactNode
}

export default function Blueprint({
  as: Component = 'div',
  corners = false,
  children,
  className = '',
  ...rest
}: BlueprintProps) {
  return (
    <Component className={`blueprint ${className}`} {...rest}>
      {corners && (
        <>
          <i aria-hidden="true" className="corner tl" />
          <i aria-hidden="true" className="corner tr" />
          <i aria-hidden="true" className="corner bl" />
          <i aria-hidden="true" className="corner br" />
        </>
      )}
      {children}
    </Component>
  )
}
