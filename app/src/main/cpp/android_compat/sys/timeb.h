#pragma once

#include <time.h>

struct timeb {
    time_t         time;
    unsigned short millitm;
    short          timezone;
    short          dstflag;
};

static inline int ftime(struct timeb *tb) {
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    if (tb) {
        tb->time     = ts.tv_sec;
        tb->millitm  = (unsigned short)(ts.tv_nsec / 1000000);
        tb->timezone = 0;
        tb->dstflag  = 0;
    }
    return 0;
}
