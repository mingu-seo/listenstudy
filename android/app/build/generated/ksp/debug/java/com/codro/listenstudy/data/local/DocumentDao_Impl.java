package com.codro.listenstudy.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.EntityUpsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class DocumentDao_Impl implements DocumentDao {
  private final RoomDatabase __db;

  private final SharedSQLiteStatement __preparedStmtOfUpdatePlayback;

  private final SharedSQLiteStatement __preparedStmtOfDeleteDocument;

  private final EntityUpsertionAdapter<DocumentEntity> __upsertionAdapterOfDocumentEntity;

  public DocumentDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__preparedStmtOfUpdatePlayback = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE documents SET lastSentenceIndex = ?, defaultSpeed = ?, updatedAt = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteDocument = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM documents WHERE id = ?";
        return _query;
      }
    };
    this.__upsertionAdapterOfDocumentEntity = new EntityUpsertionAdapter<DocumentEntity>(new EntityInsertionAdapter<DocumentEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `documents` (`id`,`title`,`sourceUri`,`localTextPath`,`totalSentences`,`createdAt`,`updatedAt`,`lastSentenceIndex`,`defaultEngine`,`defaultVoiceId`,`defaultSpeed`) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DocumentEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        if (entity.getSourceUri() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getSourceUri());
        }
        if (entity.getLocalTextPath() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getLocalTextPath());
        }
        statement.bindLong(5, entity.getTotalSentences());
        statement.bindLong(6, entity.getCreatedAt());
        statement.bindLong(7, entity.getUpdatedAt());
        statement.bindLong(8, entity.getLastSentenceIndex());
        statement.bindString(9, entity.getDefaultEngine());
        if (entity.getDefaultVoiceId() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getDefaultVoiceId());
        }
        statement.bindDouble(11, entity.getDefaultSpeed());
      }
    }, new EntityDeletionOrUpdateAdapter<DocumentEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `documents` SET `id` = ?,`title` = ?,`sourceUri` = ?,`localTextPath` = ?,`totalSentences` = ?,`createdAt` = ?,`updatedAt` = ?,`lastSentenceIndex` = ?,`defaultEngine` = ?,`defaultVoiceId` = ?,`defaultSpeed` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DocumentEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        if (entity.getSourceUri() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getSourceUri());
        }
        if (entity.getLocalTextPath() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getLocalTextPath());
        }
        statement.bindLong(5, entity.getTotalSentences());
        statement.bindLong(6, entity.getCreatedAt());
        statement.bindLong(7, entity.getUpdatedAt());
        statement.bindLong(8, entity.getLastSentenceIndex());
        statement.bindString(9, entity.getDefaultEngine());
        if (entity.getDefaultVoiceId() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getDefaultVoiceId());
        }
        statement.bindDouble(11, entity.getDefaultSpeed());
        statement.bindString(12, entity.getId());
      }
    });
  }

  @Override
  public Object updatePlayback(final String id, final int index, final float speed,
      final long updatedAt, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdatePlayback.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, index);
        _argIndex = 2;
        _stmt.bindDouble(_argIndex, speed);
        _argIndex = 3;
        _stmt.bindLong(_argIndex, updatedAt);
        _argIndex = 4;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdatePlayback.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteDocument(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteDocument.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteDocument.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertDocument(final DocumentEntity document,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfDocumentEntity.upsert(document);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<DocumentEntity>> observeDocuments() {
    final String _sql = "SELECT * FROM documents ORDER BY updatedAt DESC, createdAt DESC, id ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"documents"}, new Callable<List<DocumentEntity>>() {
      @Override
      @NonNull
      public List<DocumentEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfSourceUri = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceUri");
          final int _cursorIndexOfLocalTextPath = CursorUtil.getColumnIndexOrThrow(_cursor, "localTextPath");
          final int _cursorIndexOfTotalSentences = CursorUtil.getColumnIndexOrThrow(_cursor, "totalSentences");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfLastSentenceIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSentenceIndex");
          final int _cursorIndexOfDefaultEngine = CursorUtil.getColumnIndexOrThrow(_cursor, "defaultEngine");
          final int _cursorIndexOfDefaultVoiceId = CursorUtil.getColumnIndexOrThrow(_cursor, "defaultVoiceId");
          final int _cursorIndexOfDefaultSpeed = CursorUtil.getColumnIndexOrThrow(_cursor, "defaultSpeed");
          final List<DocumentEntity> _result = new ArrayList<DocumentEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DocumentEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpSourceUri;
            if (_cursor.isNull(_cursorIndexOfSourceUri)) {
              _tmpSourceUri = null;
            } else {
              _tmpSourceUri = _cursor.getString(_cursorIndexOfSourceUri);
            }
            final String _tmpLocalTextPath;
            if (_cursor.isNull(_cursorIndexOfLocalTextPath)) {
              _tmpLocalTextPath = null;
            } else {
              _tmpLocalTextPath = _cursor.getString(_cursorIndexOfLocalTextPath);
            }
            final int _tmpTotalSentences;
            _tmpTotalSentences = _cursor.getInt(_cursorIndexOfTotalSentences);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final int _tmpLastSentenceIndex;
            _tmpLastSentenceIndex = _cursor.getInt(_cursorIndexOfLastSentenceIndex);
            final String _tmpDefaultEngine;
            _tmpDefaultEngine = _cursor.getString(_cursorIndexOfDefaultEngine);
            final String _tmpDefaultVoiceId;
            if (_cursor.isNull(_cursorIndexOfDefaultVoiceId)) {
              _tmpDefaultVoiceId = null;
            } else {
              _tmpDefaultVoiceId = _cursor.getString(_cursorIndexOfDefaultVoiceId);
            }
            final float _tmpDefaultSpeed;
            _tmpDefaultSpeed = _cursor.getFloat(_cursorIndexOfDefaultSpeed);
            _item = new DocumentEntity(_tmpId,_tmpTitle,_tmpSourceUri,_tmpLocalTextPath,_tmpTotalSentences,_tmpCreatedAt,_tmpUpdatedAt,_tmpLastSentenceIndex,_tmpDefaultEngine,_tmpDefaultVoiceId,_tmpDefaultSpeed);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getDocuments(final Continuation<? super List<DocumentEntity>> $completion) {
    final String _sql = "SELECT * FROM documents ORDER BY updatedAt DESC, createdAt DESC, id ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<DocumentEntity>>() {
      @Override
      @NonNull
      public List<DocumentEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfSourceUri = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceUri");
          final int _cursorIndexOfLocalTextPath = CursorUtil.getColumnIndexOrThrow(_cursor, "localTextPath");
          final int _cursorIndexOfTotalSentences = CursorUtil.getColumnIndexOrThrow(_cursor, "totalSentences");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfLastSentenceIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSentenceIndex");
          final int _cursorIndexOfDefaultEngine = CursorUtil.getColumnIndexOrThrow(_cursor, "defaultEngine");
          final int _cursorIndexOfDefaultVoiceId = CursorUtil.getColumnIndexOrThrow(_cursor, "defaultVoiceId");
          final int _cursorIndexOfDefaultSpeed = CursorUtil.getColumnIndexOrThrow(_cursor, "defaultSpeed");
          final List<DocumentEntity> _result = new ArrayList<DocumentEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DocumentEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpSourceUri;
            if (_cursor.isNull(_cursorIndexOfSourceUri)) {
              _tmpSourceUri = null;
            } else {
              _tmpSourceUri = _cursor.getString(_cursorIndexOfSourceUri);
            }
            final String _tmpLocalTextPath;
            if (_cursor.isNull(_cursorIndexOfLocalTextPath)) {
              _tmpLocalTextPath = null;
            } else {
              _tmpLocalTextPath = _cursor.getString(_cursorIndexOfLocalTextPath);
            }
            final int _tmpTotalSentences;
            _tmpTotalSentences = _cursor.getInt(_cursorIndexOfTotalSentences);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final int _tmpLastSentenceIndex;
            _tmpLastSentenceIndex = _cursor.getInt(_cursorIndexOfLastSentenceIndex);
            final String _tmpDefaultEngine;
            _tmpDefaultEngine = _cursor.getString(_cursorIndexOfDefaultEngine);
            final String _tmpDefaultVoiceId;
            if (_cursor.isNull(_cursorIndexOfDefaultVoiceId)) {
              _tmpDefaultVoiceId = null;
            } else {
              _tmpDefaultVoiceId = _cursor.getString(_cursorIndexOfDefaultVoiceId);
            }
            final float _tmpDefaultSpeed;
            _tmpDefaultSpeed = _cursor.getFloat(_cursorIndexOfDefaultSpeed);
            _item = new DocumentEntity(_tmpId,_tmpTitle,_tmpSourceUri,_tmpLocalTextPath,_tmpTotalSentences,_tmpCreatedAt,_tmpUpdatedAt,_tmpLastSentenceIndex,_tmpDefaultEngine,_tmpDefaultVoiceId,_tmpDefaultSpeed);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getDocument(final String id,
      final Continuation<? super DocumentEntity> $completion) {
    final String _sql = "SELECT * FROM documents WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<DocumentEntity>() {
      @Override
      @Nullable
      public DocumentEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfSourceUri = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceUri");
          final int _cursorIndexOfLocalTextPath = CursorUtil.getColumnIndexOrThrow(_cursor, "localTextPath");
          final int _cursorIndexOfTotalSentences = CursorUtil.getColumnIndexOrThrow(_cursor, "totalSentences");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfLastSentenceIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSentenceIndex");
          final int _cursorIndexOfDefaultEngine = CursorUtil.getColumnIndexOrThrow(_cursor, "defaultEngine");
          final int _cursorIndexOfDefaultVoiceId = CursorUtil.getColumnIndexOrThrow(_cursor, "defaultVoiceId");
          final int _cursorIndexOfDefaultSpeed = CursorUtil.getColumnIndexOrThrow(_cursor, "defaultSpeed");
          final DocumentEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpSourceUri;
            if (_cursor.isNull(_cursorIndexOfSourceUri)) {
              _tmpSourceUri = null;
            } else {
              _tmpSourceUri = _cursor.getString(_cursorIndexOfSourceUri);
            }
            final String _tmpLocalTextPath;
            if (_cursor.isNull(_cursorIndexOfLocalTextPath)) {
              _tmpLocalTextPath = null;
            } else {
              _tmpLocalTextPath = _cursor.getString(_cursorIndexOfLocalTextPath);
            }
            final int _tmpTotalSentences;
            _tmpTotalSentences = _cursor.getInt(_cursorIndexOfTotalSentences);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final int _tmpLastSentenceIndex;
            _tmpLastSentenceIndex = _cursor.getInt(_cursorIndexOfLastSentenceIndex);
            final String _tmpDefaultEngine;
            _tmpDefaultEngine = _cursor.getString(_cursorIndexOfDefaultEngine);
            final String _tmpDefaultVoiceId;
            if (_cursor.isNull(_cursorIndexOfDefaultVoiceId)) {
              _tmpDefaultVoiceId = null;
            } else {
              _tmpDefaultVoiceId = _cursor.getString(_cursorIndexOfDefaultVoiceId);
            }
            final float _tmpDefaultSpeed;
            _tmpDefaultSpeed = _cursor.getFloat(_cursorIndexOfDefaultSpeed);
            _result = new DocumentEntity(_tmpId,_tmpTitle,_tmpSourceUri,_tmpLocalTextPath,_tmpTotalSentences,_tmpCreatedAt,_tmpUpdatedAt,_tmpLastSentenceIndex,_tmpDefaultEngine,_tmpDefaultVoiceId,_tmpDefaultSpeed);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
