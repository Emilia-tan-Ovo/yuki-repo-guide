package io.github.emiliatanovo.yukirepoguide.guide.domain;

public enum ReleaseAssetRole {
	STANDARD_INSTALLER,
	PORTABLE,
	MANUAL_ARCHIVE,
	AUXILIARY,
	SOURCE,
	UNKNOWN;

	public boolean isExperienceResource() {
		return this == STANDARD_INSTALLER || this == PORTABLE || this == MANUAL_ARCHIVE;
	}
}
