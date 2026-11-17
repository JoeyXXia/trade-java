import { it, expect } from 'vitest'
import { cents, money, parseProduct, quantity } from './catalog'
it('keeps prices above JS safe integers exact and formats cents without floating point', () => {
  expect(money('9007199254740993')).toBe('¥90071992547409.93')
  expect(cents('9223372036854775807')).toBe('9223372036854775807')
  for (const value of ['0', '-1', '1.5', '01', '1e2', '9223372036854775808', 100]) expect(() => cents(value)).toThrow()
})
it('rejects fractional, zero and excessive quantities rather than rounding them', () => {
  expect(quantity('999')).toBe(999)
  for (const value of ['0', '1000', '-1', '1.1', '01', ' 1']) expect(() => quantity(value)).toThrow()
})
it('rejects numeric money in API payloads', () => {
  expect(() => parseProduct({ id: '1', title: 'a', description: '', published: true, version: '2', skus: [{ id: '1', skuCode: 'A', name: 'a', priceCents: 100, enabled: true }] })).toThrow()
})
