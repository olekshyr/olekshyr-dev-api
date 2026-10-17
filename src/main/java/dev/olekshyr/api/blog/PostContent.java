package dev.olekshyr.api.blog;

record PostContent(
  String title,
  String excerpt,
  String body,
  String metaTitle,
  String metaDescription
) {}
