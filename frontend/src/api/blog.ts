import { apiFetch } from './client.ts'
import type { Items } from './catalog.ts'

// The blog (docs/TRD.md §7.19): public reads of published posts, and the admin's writing desk.

export type BlogCategory = {
  id: string
  /** Set on a sub-category; categories are one level deep. */
  parent_id: string | null
  name: string
  slug: string
  /** Published posts filed directly under it. */
  published_posts: number
}

export type BlogCategoryRef = { id: string; name: string; slug: string; parent: BlogCategoryRef | null }

export type BlogPhoto = { id: string; url: string; caption: string | null }

export type BlogPostSummary = {
  id: string
  slug: string
  title: string
  excerpt: string | null
  category: BlogCategoryRef
  cover_url: string | null
  /** null for a draft (admin only). */
  published_at: string | null
  updated_at: string
}

export type BlogPost = BlogPostSummary & {
  /** Paragraphs, "## " headings, "- " lists and the post's photos as ![caption](url) (components/blog/BlogBody.tsx). */
  body: string
  cover_photo_id: string | null
  photos: BlogPhoto[]
  author_name: string | null
}

export const listBlogCategories = () => apiFetch<Items<BlogCategory>>('/api/public/blog/categories')

export const listBlogPosts = (category?: string) =>
  apiFetch<Items<BlogPostSummary>>(
    `/api/public/blog/posts${category ? `?category=${encodeURIComponent(category)}` : ''}`,
  )

export const getBlogPost = (slug: string) => apiFetch<BlogPost>(`/api/public/blog/posts/${encodeURIComponent(slug)}`)

// --- Admin

export type BlogCategoryInput = { name: string; slug: string; parent_id: string | null }

export type BlogPostInput = { title: string; slug: string; category_id: string; excerpt: string; body: string }

const ADMIN = '/api/admin/blog'

export const adminBlogCategories = (token: string) => apiFetch<Items<BlogCategory>>(`${ADMIN}/categories`, { token })

export const createBlogCategory = (token: string, body: BlogCategoryInput) =>
  apiFetch<BlogCategory>(`${ADMIN}/categories`, { method: 'POST', token, body })

export const updateBlogCategory = (token: string, id: string, body: BlogCategoryInput) =>
  apiFetch<BlogCategory>(`${ADMIN}/categories/${id}`, { method: 'PUT', token, body })

export const deleteBlogCategory = (token: string, id: string) =>
  apiFetch<void>(`${ADMIN}/categories/${id}`, { method: 'DELETE', token })

export const adminBlogPosts = (token: string) => apiFetch<Items<BlogPostSummary>>(`${ADMIN}/posts`, { token })

export const adminBlogPost = (token: string, id: string) => apiFetch<BlogPost>(`${ADMIN}/posts/${id}`, { token })

export const createBlogPost = (token: string, body: BlogPostInput) =>
  apiFetch<BlogPost>(`${ADMIN}/posts`, { method: 'POST', token, body })

export const updateBlogPost = (token: string, id: string, body: BlogPostInput) =>
  apiFetch<BlogPost>(`${ADMIN}/posts/${id}`, { method: 'PUT', token, body })

export const setBlogPostPublished = (token: string, id: string, published: boolean) =>
  apiFetch<BlogPost>(`${ADMIN}/posts/${id}/published`, { method: 'PUT', token, body: { published } })

export const setBlogPostCover = (token: string, id: string, photoId: string | null) =>
  apiFetch<BlogPost>(`${ADMIN}/posts/${id}/cover`, { method: 'PUT', token, body: { photo_id: photoId } })

export const deleteBlogPost = (token: string, id: string) =>
  apiFetch<void>(`${ADMIN}/posts/${id}`, { method: 'DELETE', token })

export const uploadBlogPhoto = (token: string, postId: string, file: Blob, caption: string) => {
  const body = new FormData()
  body.append('file', file, 'photo.jpg')
  if (caption.trim()) body.append('caption', caption.trim())
  return apiFetch<BlogPhoto>(`${ADMIN}/posts/${postId}/photos`, { method: 'POST', token, body })
}

export const describeBlogPhoto = (token: string, postId: string, photoId: string, caption: string) =>
  apiFetch<BlogPhoto>(`${ADMIN}/posts/${postId}/photos/${photoId}`, { method: 'PUT', token, body: { caption } })

export const deleteBlogPhoto = (token: string, postId: string, photoId: string) =>
  apiFetch<void>(`${ADMIN}/posts/${postId}/photos/${photoId}`, { method: 'DELETE', token })
