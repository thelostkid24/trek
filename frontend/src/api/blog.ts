import { apiFetch } from './client.ts'
import type { Items } from './catalog.ts'

// The blog (docs/TRD.md §7.19): public reads of published posts, and the admin's writing desk.

/** A category in the admin's flat list. */
export type BlogCategory = {
  id: string
  /** Set on a sub-category; the ten categories are the top level. */
  parent_id: string | null
  name: string
  slug: string
  description: string | null
  position: number
  /** Published posts filed directly under it. */
  published_posts: number
}

/** A category on the public blog; a top-level one counts its sub-categories' posts. */
export type BlogCategoryNode = {
  id: string
  name: string
  slug: string
  description: string | null
  published_posts: number
  /** In the Blog menu (and indexed) at 3+ published posts. */
  in_menu: boolean
  subcategories: BlogCategoryNode[]
}

export type BlogCategoryPage = {
  category: BlogCategoryNode
  /** Set on a sub-category's page. */
  parent: BlogCategoryRef | null
  posts: BlogPostSummary[]
  /** false at 1–2 posts: the page works but stays out of search. */
  indexable: boolean
}

export type BlogFaq = { question: string; answer: string }

export type BlogSchemaType = 'ARTICLE' | 'FAQ_PAGE' | 'HOW_TO'

export type BlogTrekRef = { id: string; slug: string; name: string }

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
  /** Paragraphs, "## " headings, "- " lists and the post's photos as ![caption](url) (lib/blogBody.ts). */
  body: string
  cover_photo_id: string | null
  /** Shown under the hero image with the date it was taken. */
  cover_caption: string | null
  cover_taken_on: string | null
  photos: BlogPhoto[]
  author_name: string | null
  faqs: BlogFaq[]
  schema_type: BlogSchemaType
  related_treks: BlogTrekRef[]
  /** Admin only; null on the public site. */
  research_notes: string | null
}

export const listBlogCategories = () => apiFetch<Items<BlogCategoryNode>>('/api/public/blog/categories')

export const getBlogCategory = (slug: string) =>
  apiFetch<BlogCategoryPage>(`/api/public/blog/categories/${encodeURIComponent(slug)}`)

export const listBlogPosts = (limit?: number) =>
  apiFetch<Items<BlogPostSummary>>(`/api/public/blog/posts${limit ? `?limit=${limit}` : ''}`)

export const getBlogPost = (slug: string) => apiFetch<BlogPost>(`/api/public/blog/posts/${encodeURIComponent(slug)}`)

// --- Admin

export type BlogCategoryInput = { name: string; slug: string; description: string; parent_id: string | null }

export type BlogPostInput = {
  title: string
  slug: string
  category_id: string
  excerpt: string
  body: string
  cover_caption: string
  cover_taken_on: string | null
  author_name: string
  faqs: BlogFaq[]
  schema_type: BlogSchemaType
  research_notes: string
  track_ids: string[]
}

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

export const setBlogPostPublished = (token: string, id: string, published: boolean, moneyRuleConfirmed: boolean) =>
  apiFetch<BlogPost>(`${ADMIN}/posts/${id}/published`, {
    method: 'PUT',
    token,
    body: { published, money_rule_confirmed: moneyRuleConfirmed },
  })

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
