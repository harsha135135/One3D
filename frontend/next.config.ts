import type { NextConfig } from "next";

// In development the browser talks only to this server (localhost:3000), and
// these paths are forwarded to the backend, so the session cookie and the
// CSRF cookie are same-origin. In deployment Nginx does this routing (CP7).
const backend = process.env.ONE3D_BACKEND_URL ?? "http://127.0.0.1:8080";

const nextConfig: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${backend}/api/:path*` },
      { source: "/oauth2/:path*", destination: `${backend}/oauth2/:path*` },
      { source: "/login/:path*", destination: `${backend}/login/:path*` },
      { source: "/logout", destination: `${backend}/logout` },
    ];
  },
};

export default nextConfig;
