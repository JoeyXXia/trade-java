import { record } from './api'
export interface Sku { id: string; skuCode: string; name: string; priceCents: string; enabled: boolean }
export interface Product { id: string; title: string; description: string; published: boolean; version: string; skus: Sku[] }
export interface Summary { id: string; title: string; published: boolean; version: string; minPriceCents: string | null }
export interface CatalogPage { items: Summary[]; total: number; page: number; size: number }
export function cents(value: unknown): string {
  if (typeof value !== 'string' || !/^[1-9]\d{0,18}$/.test(value) || BigInt(value) > 9223372036854775807n) throw new Error('Invalid exact money')
  return value
}
function id(value: unknown): string { return cents(value) }
function version(value: unknown): string {
  if (value === '0') return value
  return cents(value)
}
function text(value: unknown): string { if (typeof value !== 'string') throw new Error('Invalid text'); return value }
function flag(value: unknown): boolean { if (typeof value !== 'boolean') throw new Error('Invalid flag'); return value }
export function money(value: string): string {
  const n = BigInt(cents(value))
  return `¥${n / 100n}.${(n % 100n).toString().padStart(2, '0')}`
}
export function parseProduct(value: unknown): Product {
  if (!record(value) || !Array.isArray(value.skus)) throw new Error('Invalid product')
  const skus = value.skus.map(s => {
    if (!record(s)) throw new Error('Invalid SKU')
    return { id: id(s.id), skuCode: text(s.skuCode), name: text(s.name), priceCents: cents(s.priceCents), enabled: flag(s.enabled) }
  })
  return { id: id(value.id), title: text(value.title), description: text(value.description), published: flag(value.published), version: version(value.version), skus }
}
export function parsePage(value: unknown): CatalogPage {
  if (!record(value) || !Array.isArray(value.items) || typeof value.total !== 'number' || !Number.isSafeInteger(value.total) || value.total < 0
    || typeof value.page !== 'number' || !Number.isInteger(value.page) || value.page < 1 || typeof value.size !== 'number' || !Number.isInteger(value.size) || value.size < 1) throw new Error('Invalid page')
  const items = value.items.map(s => {
    if (!record(s)) throw new Error('Invalid summary')
    return { id: id(s.id), title: text(s.title), published: flag(s.published), version: version(s.version), minPriceCents: s.minPriceCents === null ? null : cents(s.minPriceCents) }
  })
  return { items, total: value.total, page: value.page, size: value.size }
}
export function quantity(value: string): number {
  if (!/^[1-9]\d{0,2}$/.test(value)) throw new Error('Quantity must be 1–999')
  return Number(value)
}
export function quoteTotal(value: unknown): string {
  if (!record(value) || value.currency !== 'CNY') throw new Error('Invalid quote')
  return cents(value.totalCents)
}
