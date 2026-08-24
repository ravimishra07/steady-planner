import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  // A stray package-lock.json in the home directory otherwise gets picked as the root.
  turbopack: { root: __dirname },
};

export default nextConfig;
