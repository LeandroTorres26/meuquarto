package com.meuquarto.media.domain;

/** A link to a work's own page on one platform; the url is built in {@link MediaLinks} on a fixed host. */
public record ItemLink(LinkPlatform platform, String url) {}
