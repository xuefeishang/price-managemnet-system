import type { MenuItem } from '@/types'

export type MenuNode = Omit<MenuItem, 'children'> & {
  children: MenuNode[]
}

export interface MenuMatch {
  node: MenuNode
  ancestors: MenuNode[]
}

export const sortMenuNodes = (nodes: MenuNode[]): MenuNode[] => {
  return [...nodes]
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map(node => ({
      ...node,
      children: sortMenuNodes(node.children || [])
    }))
}

export const normalizeMenuTree = (menus: MenuItem[]): MenuNode[] => {
  const map = new Map<number, MenuNode>()

  const ensureNode = (menu: MenuItem): MenuNode => {
    const existing = map.get(menu.id)
    const children = (menu.children || []).map(child => ensureNode(child))
    const node: MenuNode = {
      ...menu,
      children: children.length > 0 ? children : existing?.children || []
    }
    map.set(menu.id, node)
    return node
  }

  menus.forEach(menu => ensureNode(menu))

  for (const menu of menus) {
    if (menu.children && menu.children.length > 0) continue
    if (menu.parentId !== null) {
      const parent = map.get(menu.parentId)
      const node = map.get(menu.id)
      if (parent && node && !parent.children.some(child => child.id === node.id)) {
        parent.children.push(node)
      }
    }
  }

  const roots = menus
    .filter(menu => menu.parentId === null)
    .map(menu => map.get(menu.id))
    .filter((node): node is MenuNode => Boolean(node))

  return sortMenuNodes(roots)
}

export const findMenuByPath = (
  nodes: MenuNode[],
  path: string | undefined,
  ancestors: MenuNode[] = []
): MenuMatch | null => {
  if (!path) return null

  for (const node of nodes) {
    if (node.path === path) {
      return { node, ancestors }
    }

    const match = findMenuByPath(node.children, path, [...ancestors, node])
    if (match) return match
  }

  return null
}

export const collectMenuIds = (nodes: MenuNode[]): number[] => {
  return nodes.flatMap(node => [node.id, ...collectMenuIds(node.children)])
}

export const getMenuDepth = (match: MenuMatch | null): number => {
  if (!match) return 0
  return match.ancestors.length + 1
}
