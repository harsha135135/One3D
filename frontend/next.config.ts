import type { NextConfig } from "next";

// In development the browser talks only to this server (localhost:3000), and
// these paths are forwarded to the backend, so the session cookie and the
// CSRF cookie are same-origin. In deployment Nginx does this routing (CP7).
const backend = process.env.ONE3D_BACKEND_URL ?? "http://127.0.0.1:8080";
// Local only: browser uploads and downloads use presigned URLs under /s3 on
// this origin, forwarded to the S3-compatible mock. On AWS they go to S3.
const storage = process.env.ONE3D_STORAGE_PROXY_URL ?? "http://127.0.0.1:9090";

const nextConfig: NextConfig = {
  // The Docker image (Checkpoint 7) builds a self-contained server; local runs use "next start".
  output: process.env.ONE3D_STANDALONE === "true" ? "standalone" : undefined,
  poweredByHeader: false,
  experimental: {
    // Uploads through the local /s3 route can be up to 20 MB; the default
    // forwarding limit is 10 MB. On AWS the browser uploads straight to S3.
    proxyClientMaxBodySize: "25mb",
  },
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${backend}/api/:path*` },
      { source: "/oauth2/:path*", destination: `${backend}/oauth2/:path*` },
      { source: "/login/:path*", destination: `${backend}/login/:path*` },
      { source: "/logout", destination: `${backend}/logout` },
      { source: "/s3/:path*", destination: `${storage}/:path*` },
    ];
  },
};

export default nextConfig;
