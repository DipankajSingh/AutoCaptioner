import './globals.css';

export const metadata = {
  metadataBase: new URL('https://autocaptioner.app/'),
  title: 'AutoCaptioner - Private, On-Device AI Video Captions',
  description: 'Generate perfectly synced captions directly on your Android using local Whisper AI. Fast, private, and customizable with no cloud required.',
  keywords: ['captions', 'video captions', 'auto captions', 'on-device AI', 'whisper AI', 'android app', 'private captions'],
  authors: [{ name: 'AutoCaptioner' }],
  openGraph: {
    title: 'AutoCaptioner - Private, On-Device AI Video Captions',
    description: 'Generate perfectly synced captions directly on your Android using local Whisper AI. Fast, private, and customizable with no cloud required.',
    url: 'https://autocaptioner.app/',
    siteName: 'AutoCaptioner',
    images: [
      {
        url: '/og-image.jpg',
        width: 1200,
        height: 630,
      },
    ],
    locale: 'en_US',
    type: 'website',
  },
  twitter: {
    card: 'summary_large_image',
    title: 'AutoCaptioner - Private, On-Device AI Video Captions',
    description: 'Generate perfectly synced captions directly on your Android using local Whisper AI. Fast, private, and customizable with no cloud required.',
    images: ['/og-image.jpg'],
  },
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body className="antialiased font-sans bg-deepSpace text-textPrimary selection:bg-accentBlue/30 selection:text-white">
        {children}
      </body>
    </html>
  );
}
