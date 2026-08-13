import { describe, expect, it } from 'vitest'
import { extractWaylineUuid, parseNameFromBody } from '../waylineNameSync'

describe('waylineNameSync', () => {
  it('从司空改名 URL 提取 wayline uuid', () => {
    const url =
      '/paas-api/gateway/openapi/v0.9/wayline/api/v1/workspaces/abc/waylines/715cbd4e-0c07-4230-a40b-8e63acc1cefe'
    expect(extractWaylineUuid(url)).toBe('715cbd4e-0c07-4230-a40b-8e63acc1cefe')
  })

  it('忽略 check-name', () => {
    expect(
      extractWaylineUuid('/openapi/v0.9/wayline/api/v1/workspaces/x/waylines/check-name?name=a'),
    ).toBeNull()
  })

  it('解析改名请求体中的 name', () => {
    expect(parseNameFromBody(JSON.stringify({ name: '新航线' }))).toBe('新航线')
    expect(parseNameFromBody({ name: ' 航线A ' })).toBe('航线A')
  })
})
