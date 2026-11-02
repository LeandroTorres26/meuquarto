import { describe, expect, it } from "vitest";
import { isHttpsUrl, markClass, shownPlatformLinks } from "./platforms";

describe("shownPlatformLinks", () => {
  it("keeps the order of the API and adds the display name", () => {
    expect(
      shownPlatformLinks([
        { platform: "LETTERBOXD", url: "https://letterboxd.com/ana/" },
        { platform: "LASTFM", url: "https://www.last.fm/user/ana" },
        { platform: "STORYGRAPH", url: "https://app.thestorygraph.com/profile/ana" },
      ]),
    ).toEqual([
      { platform: "LETTERBOXD", name: "Letterboxd", url: "https://letterboxd.com/ana/" },
      { platform: "LASTFM", name: "Last.fm", url: "https://www.last.fm/user/ana" },
      {
        platform: "STORYGRAPH",
        name: "StoryGraph",
        url: "https://app.thestorygraph.com/profile/ana",
      },
    ]);
  });

  it("drops unknown platforms and anything that is not an https link", () => {
    expect(
      shownPlatformLinks([
        { platform: "MYSPACE", url: "https://myspace.com/ana" },
        { platform: "TRAKT", url: "javascript:alert(1)" },
        { platform: "TRAKT", url: "http://trakt.tv/users/ana" },
        { platform: "TRAKT", url: "not a url" },
        { platform: "BACKLOGGD", url: "https://backloggd.com/u/ana/" },
      ]),
    ).toEqual([{ platform: "BACKLOGGD", name: "Backloggd", url: "https://backloggd.com/u/ana/" }]);
  });

  it("is empty without links", () => {
    expect(shownPlatformLinks(undefined)).toEqual([]);
    expect(shownPlatformLinks([])).toEqual([]);
  });
});

describe("markClass and isHttpsUrl", () => {
  it("names the tile modifier after the platform", () => {
    expect(markClass("MYANIMELIST")).toBe("mq-mark--myanimelist");
  });

  it("accepts https only", () => {
    expect(isHttpsUrl("https://trakt.tv/users/ana")).toBe(true);
    expect(isHttpsUrl("http://trakt.tv/users/ana")).toBe(false);
    expect(isHttpsUrl("//trakt.tv/users/ana")).toBe(false);
  });
});
