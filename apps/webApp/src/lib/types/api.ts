// Mirrors the Kotlin @Serializable contracts in shared/{common,authentication,security,content,search,playback,watchlist,profile}.
// Field names and shapes must stay in sync with those modules; see docs/api/openapi.yaml for the (partially stale) OpenAPI doc.

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

// --- auth ---

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

// --- profiles ---

export type ProfileKind = "STANDARD" | "KIDS";

export interface Profile {
  id: string;
  userId: string;
  name: string;
  kind: ProfileKind;
  language: string;
  subtitleLanguage: string | null;
  avatarKey: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProfileCommand {
  name: string;
  kind?: ProfileKind;
  language?: string;
  subtitleLanguage?: string | null;
  avatarKey?: string | null;
}

export interface UpdateProfileCommand {
  name: string;
  language: string;
  subtitleLanguage: string | null;
  avatarKey: string | null;
}

// --- content ---

export type ContentType = "MOVIE" | "SERIES" | "SEASON" | "EPISODE" | "TRAILER" | "SHORT";
export type AccessTier = "FREE" | "PREMIUM";

export interface PersonCredit {
  name: string;
  role: string;
  character?: string | null;
}

export interface SubtitleTrack {
  language: string;
  label: string;
  uri?: string | null;
}

export interface AudioTrack {
  language: string;
  label: string;
  isDefault: boolean;
}

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

export interface ContentDetail {
  summary: ContentSummary;
  genres: string[];
  credits: PersonCredit[];
  audioTracks: AudioTrack[];
  subtitleTracks: SubtitleTrack[];
  children: ContentSummary[];
  playable: boolean;
}

export interface ContentSection {
  key: string;
  title: string;
  items: ContentSummary[];
}

export interface HomeFeed {
  sections: ContentSection[];
}

export interface ContentListParams {
  type?: ContentType;
  genre?: string;
  releaseYear?: number;
  cursor?: string;
  limit?: number;
}

// --- search ---

export interface SearchResult {
  items: ContentSummary[];
  nextCursor?: string | null;
  total?: number | null;
}

export interface AutocompleteSuggestion {
  text: string;
  contentId: string;
  type: ContentType;
}

export interface SearchHistoryEntry {
  query: string;
  searchedAt: string;
}

// --- playback ---

export interface PlaybackSession {
  id: string;
  userId: string;
  profileId: string;
  contentId: string;
  startedAt: string;
  expiresAt: string;
}

export interface PlaybackGrant {
  session: PlaybackSession;
  hlsUrl: string | null;
  dashUrl: string | null;
  playbackToken: string;
  expiresAt: string;
}

export interface PlaybackPosition {
  sessionId: string;
  contentId: string;
  positionSeconds: number;
  durationSeconds: number | null;
  completed: boolean;
  updatedAt: string;
}

export interface ContinueWatchingItem {
  content: ContentSummary;
  position: PlaybackPosition;
}

export interface StartPlaybackCommand {
  contentId: string;
  profileId: string;
}

export interface UpdatePositionCommand {
  positionSeconds: number;
  durationSeconds?: number | null;
  completed?: boolean;
}

// --- watchlist ---

export interface WatchlistEntry {
  content: ContentSummary;
  addedAt: string;
}
