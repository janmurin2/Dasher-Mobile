set(KENLM_ROOT ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/kenlm)
set(KENLM_MAX_ORDER 6 CACHE STRING "Maximum supported KenLM ngram order")

set(KENLM_DOUBLE_CONV_SRC
    ${KENLM_ROOT}/util/double-conversion/bignum.cc
    ${KENLM_ROOT}/util/double-conversion/bignum-dtoa.cc
    ${KENLM_ROOT}/util/double-conversion/cached-powers.cc
    ${KENLM_ROOT}/util/double-conversion/double-to-string.cc
    ${KENLM_ROOT}/util/double-conversion/fast-dtoa.cc
    ${KENLM_ROOT}/util/double-conversion/fixed-dtoa.cc
    ${KENLM_ROOT}/util/double-conversion/string-to-double.cc
    ${KENLM_ROOT}/util/double-conversion/strtod.cc
)

set(KENLM_UTIL_SRC
    ${KENLM_ROOT}/util/bit_packing.cc
    ${KENLM_ROOT}/util/ersatz_progress.cc
    ${KENLM_ROOT}/util/exception.cc
    ${KENLM_ROOT}/util/file.cc
    ${KENLM_ROOT}/util/file_piece.cc
    ${KENLM_ROOT}/util/float_to_string.cc
    ${KENLM_ROOT}/util/integer_to_string.cc
    ${KENLM_ROOT}/util/mmap.cc
    ${KENLM_ROOT}/util/murmur_hash.cc
    ${KENLM_ROOT}/util/parallel_read.cc
    ${KENLM_ROOT}/util/pool.cc
    ${KENLM_ROOT}/util/read_compressed.cc
    ${KENLM_ROOT}/util/scoped.cc
    ${KENLM_ROOT}/util/spaces.cc
    ${KENLM_ROOT}/util/string_piece.cc
    ${KENLM_ROOT}/util/usage.cc
)

set(KENLM_LM_SRC
    ${KENLM_ROOT}/lm/bhiksha.cc
    ${KENLM_ROOT}/lm/binary_format.cc
    ${KENLM_ROOT}/lm/config.cc
    ${KENLM_ROOT}/lm/lm_exception.cc
    ${KENLM_ROOT}/lm/model.cc
    ${KENLM_ROOT}/lm/quantize.cc
    ${KENLM_ROOT}/lm/read_arpa.cc
    ${KENLM_ROOT}/lm/search_hashed.cc
    ${KENLM_ROOT}/lm/search_trie.cc
    ${KENLM_ROOT}/lm/sizes.cc
    ${KENLM_ROOT}/lm/trie.cc
    ${KENLM_ROOT}/lm/trie_sort.cc
    ${KENLM_ROOT}/lm/value_build.cc
    ${KENLM_ROOT}/lm/virtual_interface.cc
    ${KENLM_ROOT}/lm/vocab.cc
)

add_library(kenlm_query STATIC
    ${KENLM_DOUBLE_CONV_SRC}
    ${KENLM_UTIL_SRC}
    ${KENLM_LM_SRC}
)

target_include_directories(kenlm_query PUBLIC ${KENLM_ROOT})
target_compile_definitions(kenlm_query PUBLIC
    KENLM_MAX_ORDER=${KENLM_MAX_ORDER}
    NDEBUG
)
target_compile_definitions(kenlm_query PRIVATE _LIBCPP_ENABLE_CXX17_REMOVED_UNARY_BINARY_FUNCTION)
target_compile_options(kenlm_query PRIVATE -fPIC)

find_package(Threads REQUIRED)
target_link_libraries(kenlm_query PUBLIC Threads::Threads)


