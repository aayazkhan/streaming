import { FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useCreateProfile, useProfiles } from "../lib/hooks/useProfiles";
import { useActiveProfile } from "../lib/profile/ActiveProfileContext";
import { Spinner } from "../components/Spinner";
import { ErrorBanner } from "../components/ErrorBanner";

export function ProfileSelectPage() {
  const { data: profiles, isLoading, error } = useProfiles();
  const createProfile = useCreateProfile();
  const { setActiveProfileId } = useActiveProfile();
  const navigate = useNavigate();
  const [newProfileName, setNewProfileName] = useState("");
  const [isCreating, setIsCreating] = useState(false);

  const selectProfile = (profileId: string) => {
    setActiveProfileId(profileId);
    navigate("/", { replace: true });
  };

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault();
    if (!newProfileName.trim()) return;
    setIsCreating(true);
    try {
      const profile = await createProfile.mutateAsync({ name: newProfileName.trim() });
      selectProfile(profile.id);
    } finally {
      setIsCreating(false);
    }
  };

  if (isLoading) return <Spinner label="Loading profiles" />;

  return (
    <div className="flex min-h-screen flex-col items-center justify-center px-4">
      <h1 className="mb-8 text-3xl font-bold">Who's watching?</h1>
      <ErrorBanner error={error} />
      <div className="mb-8 flex flex-wrap justify-center gap-6">
        {profiles?.map((profile) => (
          <button
            key={profile.id}
            onClick={() => selectProfile(profile.id)}
            className="group flex flex-col items-center gap-2"
          >
            <div className="flex h-24 w-24 items-center justify-center rounded-md bg-surface-raised text-2xl font-bold text-white/80 group-hover:ring-2 group-hover:ring-white">
              {profile.name.charAt(0).toUpperCase()}
            </div>
            <span className="text-white/80 group-hover:text-white">{profile.name}</span>
          </button>
        ))}
      </div>
      <form onSubmit={handleCreate} className="flex items-center gap-2">
        <input
          value={newProfileName}
          onChange={(event) => setNewProfileName(event.target.value)}
          placeholder="New profile name"
          aria-label="New profile name"
          className="rounded-md bg-surface-raised px-3 py-2 text-white placeholder:text-white/40 focus:outline-none focus:ring-2 focus:ring-brand"
        />
        <button
          type="submit"
          disabled={isCreating}
          className="rounded-md border border-white/30 px-4 py-2 text-sm text-white/80 hover:border-white hover:text-white disabled:opacity-60"
        >
          {isCreating ? "Adding..." : "Add profile"}
        </button>
      </form>
    </div>
  );
}
