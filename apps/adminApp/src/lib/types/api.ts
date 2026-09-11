// Mirrors the Kotlin @Serializable contracts in shared/{common,authentication,security,content}
// and the new admin-authoring additions in shared/content/ContentModels.kt.

export interface ApiErrorResponse {
  code: string;
  message: string;
  correlationId: string;
  details?: Record<string, string>;
}

export interface Page<T> {
  items: T[];
  nextCursor?: string | null;
  hasMore: boolean;
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresAt: string;
}

export interface Session {
  userId: string;
  deviceId: string;
  tokens: TokenPair;
}

export interface UserSummary {
  id: string;
  email: string;
  displayName: string;
  emailVerified: boolean;
}

export interface AuthenticationResponse {
  user: UserSummary;
  session: Session;
}

export type ContentType = "MOVIE" | "SERIES" | "SEASON" | "EPISODE" | "TRAILER" | "SHORT";
export type AccessTier = "FREE" | "PREMIUM";
export type ContentStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

export interface ContentSummary {
  id: string;
  type: ContentType;
  title: string;
  synopsis: string | null;
  posterUrl: string | null;
  backdropUrl: string | null;
  releaseYear: number | null;
  durationSeconds: number | null;
  accessTier: AccessTier;
  rating: number | null;
  createdAt: string;
}

export interface AdminContentSummary {
  summary: ContentSummary;
  status: ContentStatus;
}

export interface CreateContentCommand {
  type: ContentType;
  title: string;
  synopsis?: string | null;
  releaseYear?: number | null;
  durationSeconds?: number | null;
  accessTier: AccessTier;
  status: ContentStatus;
  isFeatured: boolean;
  genreNames: string[];
}

export type UpdateContentCommand = Omit<CreateContentCommand, "type">;

export interface Genre {
  id: string;
  name: string;
}

export type MediaUploadStatus = "REQUESTED" | "UPLOADING" | "UPLOADED" | "PROCESSING" | "READY" | "FAILED";

export interface MediaUpload {
  id: string;
  contentId: string;
  objectKey: string;
  fileName: string;
  contentType: string;
  expectedSizeBytes: number;
  checksumSha256: string | null;
  status: MediaUploadStatus;
  createdAt: string;
  completedAt: string | null;
}

export interface CreateMediaUploadRequest {
  contentId: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  checksumSha256?: string | null;
}

export interface PresignedObjectGrant {
  key: string;
  method: string;
  url: string;
  expiresAt: string;
  requiredHeaders: Record<string, string>;
}

export interface MediaUploadGrant {
  upload: MediaUpload;
  grant: PresignedObjectGrant;
}

export interface StorageObjectMetadata {
  key: string;
  sizeBytes: number;
  contentType: string | null;
  etag: string | null;
}

export interface CompletedMediaUpload {
  upload: MediaUpload;
  objectMetadata: StorageObjectMetadata;
  processingQueued: boolean;
}

export type MediaJobState =
  | "ACCEPTED"
  | "UPLOADING"
  | "TRANSCODING"
  | "PACKAGING"
  | "SUBTITLES"
  | "THUMBNAILS"
  | "READY"
  | "FAILED"
  | "CANCELLED";

export interface MediaJob {
  id: string;
  contentId: string;
  sourceKey: string;
  state: MediaJobState;
  createdAt: string;
  updatedAt: string;
  failureCode?: string | null;
}
