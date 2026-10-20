/** 用后端开发密钥签 AiFlow JWT（绕过登录，仅本地联调用）。用法：node sign-jwt.js */
const crypto = require('crypto')

const secret = 'UAVPlatformDevSecretKeyMustBeLongEnoughForHS256Algorithm'
const b64u = (o) => Buffer.from(JSON.stringify(o)).toString('base64url')
const now = Math.floor(Date.now() / 1000)

const header = { alg: 'HS256', typ: 'JWT' }
const payload = {
  sub: '1',
  workspaceId: 1,
  roleCode: 'super_admin',
  permissions: [
    'wayline:read', 'wayline:write', 'wayline:delete',
    'device:read', 'mission:read', 'mission:write',
    'dashboard:read', 'media:read', 'safety:read',
  ],
  iat: now,
  exp: now + 86400,
}

const data = b64u(header) + '.' + b64u(payload)
const sig = crypto.createHmac('sha256', secret).update(data).digest('base64url')
const token = data + '.' + sig

console.log(token)
