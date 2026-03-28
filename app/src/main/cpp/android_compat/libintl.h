#pragma once

static inline const char *gettext(const char *s) { return s; }
static inline const char *ngettext(const char *s1, const char *, unsigned long) { return s1; }

#ifndef _
#define _(s)  (s)
#endif
#ifndef N_
#define N_(s) (s)
#endif
