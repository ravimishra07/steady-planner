import { ReactNode } from 'react';

export function Phone({ children }: { children: ReactNode }) {
  return <div className="phone">{children}</div>;
}

export function Body({ children }: { children: ReactNode }) {
  return <div className="body">{children}</div>;
}

export function Foot({ children }: { children: ReactNode }) {
  return <div className="foot">{children}</div>;
}
