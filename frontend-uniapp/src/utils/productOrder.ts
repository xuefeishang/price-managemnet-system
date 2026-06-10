import type { Product, ProductCategory } from '@/types'

export const getProductCategoryId = (product: Product) =>
  product.categoryId ?? product.category?.id ?? null

export const sortProductsByHomeOrder = (
  products: Product[],
  categories: ProductCategory[]
) => {
  const categorySortMap = new Map(categories.map(category => [category.id, category.sortOrder]))

  const getCategorySortOrder = (product: Product) => {
    const categoryId = getProductCategoryId(product)
    if (categoryId == null) return Number.MAX_SAFE_INTEGER
    return categorySortMap.get(categoryId)
      ?? product.category?.sortOrder
      ?? Number.MAX_SAFE_INTEGER
  }

  return [...products].sort((a, b) =>
    getCategorySortOrder(a) - getCategorySortOrder(b)
    || (a.sortOrder ?? 0) - (b.sortOrder ?? 0)
    || a.name.localeCompare(b.name, 'zh-CN')
    || a.id - b.id
  )
}
