package com.sendbird.mylibrary.data.source;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sendbird.mylibrary.data.Book;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class BooksRepository implements BooksDataSource {

    private static BooksRepository INSTANCE = null;

    private final BooksDataSource mBooksRemoteDataSource;

    private final BooksDataSource mBooksLocalDataSource;

    Map<String, Book> mCachedBooks;

    boolean mCacheIsDirty = false;

    // Prevent direct instantiation.
    private BooksRepository(@NonNull BooksDataSource booksRemoteDataSource,
                            @NonNull BooksDataSource booksLocalDataSource) {
        mBooksRemoteDataSource = checkNotNull(booksRemoteDataSource);
        mBooksLocalDataSource = checkNotNull(booksLocalDataSource);
    }

    public static BooksRepository getInstance(BooksDataSource booksRemoteDataSource,
                                              BooksDataSource booksLocalDataSource) {
        if (INSTANCE == null) {
            INSTANCE = new BooksRepository(booksRemoteDataSource, booksLocalDataSource);
        }
        return INSTANCE;
    }

    public static void destroyInstance() {
        INSTANCE = null;
    }

    @Override
    public void getBooks(@NonNull final LoadBooksCallback callback) {
        checkNotNull(callback);

        // Respond immediately with cache if available and not dirty
        if (mCachedBooks != null && !mCacheIsDirty) {
            callback.onBooksLoaded(new ArrayList<>(mCachedBooks.values()));
            return;
        }

        if (mCacheIsDirty) {
            // If the cache is dirty we need to fetch new data from the network.
            getBooksFromRemoteDataSource(callback);
        } else {
            // Query the local storage if available. If not, query the network.
            mBooksLocalDataSource.getBooks(new LoadBooksCallback() {
                @Override
                public void onBooksLoaded(List<Book> books) {
                    refreshCache(books);
                    callback.onBooksLoaded(new ArrayList<>(mCachedBooks.values()));
                }

                @Override
                public void onDataNotAvailable() {
                    getBooksFromRemoteDataSource(callback);
                }
            });
        }
    }

    @Override
    public void getBook(@NonNull final String bookId, @NonNull final GetBookCallback callback) {
        checkNotNull(bookId);
        checkNotNull(callback);

        Book cachedBook = getBookWithId(bookId);

        // Respond immediately with cache if available
        if (cachedBook != null && cachedBook.getIsbn10() != null) {
            callback.onBookLoaded(cachedBook);
            return;
        }

        // Load from server/persisted if needed.

        // Is the task in the local data source? If not, query the network.
        mBooksLocalDataSource.getBook(bookId, new GetBookCallback() {
            @Override
            public void onBookLoaded(Book book) {
                // Do in memory cache update to keep the app UI up to date
                if (mCachedBooks == null) {
                    mCachedBooks = new LinkedHashMap<>();
                }
                mCachedBooks.put(book.getId(), book);
                callback.onBookLoaded(book);
            }

            @Override
            public void onDataNotAvailable() {
                mBooksRemoteDataSource.getBook(bookId, new GetBookCallback() {
                    @Override
                    public void onBookLoaded(Book book) {
                        // Do in memory cache update to keep the app UI up to date
                        if (mCachedBooks == null) {
                            mCachedBooks = new LinkedHashMap<>();
                        }
                        mCachedBooks.put(book.getId(), book);
                        mBooksLocalDataSource.saveBook(book);
                        callback.onBookLoaded(book);
                    }

                    @Override
                    public void onDataNotAvailable() {
                        callback.onDataNotAvailable();
                    }
                });
            }
        });
    }

    @Override
    public void addBookmark(@NonNull Book book) {
        checkNotNull(book);
        mBooksRemoteDataSource.addBookmark(book);
        mBooksLocalDataSource.addBookmark(book);

        Book updatedBook = new Book(book.getTitle(),
                book.getSubtitle(),
                book.getId(),
                book.getPrice(),
                book.getImage(),
                book.getUrl(),
                book.getAuthors(),
                book.getPublisher(),
                book.getLanguage(),
                book.getIsbn10(),
                book.getPages(),
                book.getYear(),
                book.getRating(),
                book.getDesc(),
                true);

        // Do in memory cache update to keep the app UI up to date
        if (mCachedBooks == null) {
            mCachedBooks = new LinkedHashMap<>();
        }
        mCachedBooks.put(book.getId(), updatedBook);
    }

    @Override
    public void removeBookmark(@NonNull Book book) {
        checkNotNull(book);
        mBooksRemoteDataSource.removeBookmark(book);
        mBooksLocalDataSource.removeBookmark(book);

        Book updatedBook = new Book(book.getTitle(),
                                    book.getSubtitle(),
                                    book.getId(),
                                    book.getPrice(),
                                    book.getImage(),
                                    book.getUrl(),
                                    book.getAuthors(),
                                    book.getPublisher(),
                                    book.getLanguage(),
                                    book.getIsbn10(),
                                    book.getPages(),
                                    book.getYear(),
                                    book.getRating(),
                                    book.getDesc(),
                                    false);

        // Do in memory cache update to keep the app UI up to date
        if (mCachedBooks == null) {
            mCachedBooks = new LinkedHashMap<>();
        }
        mCachedBooks.put(book.getId(), updatedBook);
    }

    @Override
    public void searchBooks(@NonNull String query, @NonNull final LoadBooksCallback callback) {
        mBooksRemoteDataSource.searchBooks(query, new LoadBooksCallback() {
            @Override
            public void onBooksLoaded(List<Book> books) {
                callback.onBooksLoaded(books);
            }

            @Override
            public void onDataNotAvailable() {
                callback.onDataNotAvailable();
            }
        });
    }

    @Override
    public void saveBook(@NonNull Book book) {
        checkNotNull(book);
        mBooksRemoteDataSource.saveBook(book);
        mBooksLocalDataSource.saveBook(book);

        // Do in memory cache update to keep the app UI up to date
        if (mCachedBooks == null) {
            mCachedBooks = new LinkedHashMap<>();
        }
        mCachedBooks.put(book.getId(), book);
    }

    @Override
    public void refreshBooks() {
        mCacheIsDirty = true;
    }

    @Override
    public void deleteAllBooks() {
        mBooksRemoteDataSource.deleteAllBooks();
        mBooksLocalDataSource.deleteAllBooks();

        if (mCachedBooks == null) {
            mCachedBooks = new LinkedHashMap<>();
        }
        mCachedBooks.clear();
    }

    @Override
    public void deleteBook(@NonNull String bookId) {
        mBooksRemoteDataSource.deleteBook(checkNotNull(bookId));
        mBooksLocalDataSource.deleteBook(checkNotNull(bookId));

        mCachedBooks.remove(bookId);
    }

    private void getBooksFromRemoteDataSource(@NonNull final LoadBooksCallback callback) {
        mBooksRemoteDataSource.getBooks(new LoadBooksCallback() {

            @Override
            public void onBooksLoaded(List<Book> books) {
                refreshCache(books);
                refreshLocalDataSource(books);
                callback.onBooksLoaded(new ArrayList<>(mCachedBooks.values()));
            }

            @Override
            public void onDataNotAvailable() {
                callback.onDataNotAvailable();
            }
        });
    }

    private void refreshCache(List<Book> books) {
        if (mCachedBooks == null) {
            mCachedBooks = new LinkedHashMap<>();
        }
        mCachedBooks.clear();
        for (Book book : books) {
            mCachedBooks.put(book.getId(), book);
        }
        mCacheIsDirty = false;
    }

    private void refreshLocalDataSource(List<Book> books) {
        mBooksLocalDataSource.deleteAllBooks();
        for (Book book : books) {
            mBooksLocalDataSource.saveBook(book);
        }
    }

    @Nullable
    private Book getBookWithId(@NonNull String id) {
        checkNotNull(id);
        if (mCachedBooks == null || mCachedBooks.isEmpty()) {
            return null;
        } else {
            return mCachedBooks.get(id);
        }
    }
}
