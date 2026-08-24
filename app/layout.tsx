import type { Metadata, Viewport } from 'next';
import {
  Bricolage_Grotesque, IBM_Plex_Mono, IBM_Plex_Sans, IBM_Plex_Sans_Devanagari,
} from 'next/font/google';
import { PlanProvider } from '@/lib/state';
import './globals.css';

const display = Bricolage_Grotesque({
  variable: '--font-display', subsets: ['latin'], weight: ['400', '600', '800'],
});
const sans = IBM_Plex_Sans({
  variable: '--font-sans', subsets: ['latin'], weight: ['400', '500', '600'],
});
const mono = IBM_Plex_Mono({
  variable: '--font-mono', subsets: ['latin'], weight: ['400', '500', '600'],
});
const devanagari = IBM_Plex_Sans_Devanagari({
  variable: '--font-devanagari', subsets: ['devanagari'], weight: ['400', '500', '600'],
});

export const metadata: Metadata = {
  title: 'Exam planner',
  description: 'A study plan built backward from your exam date.',
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  themeColor: '#20262E',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  const fonts = [display.variable, sans.variable, mono.variable, devanagari.variable].join(' ');
  return (
    <html lang="en" className={fonts}>
      <body>
        <PlanProvider>{children}</PlanProvider>
      </body>
    </html>
  );
}
