package com.codro.listenstudy.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
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
public final class SentenceDao_Impl implements SentenceDao {
  private final RoomDatabase __db;

  private final SharedSQLiteStatement __preparedStmtOfDeleteSentences;

  private final EntityUpsertionAdapter<SentenceEntity> __upsertionAdapterOfSentenceEntity;

  public SentenceDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__preparedStmtOfDeleteSentences = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM sentences WHERE documentId = ?";
        return _query;
      }
    };
    this.__upsertionAdapterOfSentenceEntity = new EntityUpsertionAdapter<SentenceEntity>(new EntityInsertionAdapter<SentenceEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `sentences` (`id`,`documentId`,`sentenceIndex`,`text`,`startOffset`,`endOffset`,`cachedAudioPath`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SentenceEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getDocumentId());
        statement.bindLong(3, entity.getSentenceIndex());
        statement.bindString(4, entity.getText());
        statement.bindLong(5, entity.getStartOffset());
        statement.bindLong(6, entity.getEndOffset());
        if (entity.getCachedAudioPath() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getCachedAudioPath());
        }
      }
    }, new EntityDeletionOrUpdateAdapter<SentenceEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `sentences` SET `id` = ?,`documentId` = ?,`sentenceIndex` = ?,`text` = ?,`startOffset` = ?,`endOffset` = ?,`cachedAudioPath` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SentenceEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getDocumentId());
        statement.bindLong(3, entity.getSentenceIndex());
        statement.bindString(4, entity.getText());
        statement.bindLong(5, entity.getStartOffset());
        statement.bindLong(6, entity.getEndOffset());
        if (entity.getCachedAudioPath() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getCachedAudioPath());
        }
        statement.bindString(8, entity.getId());
      }
    });
  }

  @Override
  public Object deleteSentences(final String documentId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteSentences.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, documentId);
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
          __preparedStmtOfDeleteSentences.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertSentences(final List<SentenceEntity> sentences,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfSentenceEntity.upsert(sentences);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<SentenceEntity>> observeSentences(final String documentId) {
    final String _sql = "SELECT * FROM sentences WHERE documentId = ? ORDER BY sentenceIndex ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, documentId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"sentences"}, new Callable<List<SentenceEntity>>() {
      @Override
      @NonNull
      public List<SentenceEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfDocumentId = CursorUtil.getColumnIndexOrThrow(_cursor, "documentId");
          final int _cursorIndexOfSentenceIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "sentenceIndex");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfStartOffset = CursorUtil.getColumnIndexOrThrow(_cursor, "startOffset");
          final int _cursorIndexOfEndOffset = CursorUtil.getColumnIndexOrThrow(_cursor, "endOffset");
          final int _cursorIndexOfCachedAudioPath = CursorUtil.getColumnIndexOrThrow(_cursor, "cachedAudioPath");
          final List<SentenceEntity> _result = new ArrayList<SentenceEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SentenceEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpDocumentId;
            _tmpDocumentId = _cursor.getString(_cursorIndexOfDocumentId);
            final int _tmpSentenceIndex;
            _tmpSentenceIndex = _cursor.getInt(_cursorIndexOfSentenceIndex);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final int _tmpStartOffset;
            _tmpStartOffset = _cursor.getInt(_cursorIndexOfStartOffset);
            final int _tmpEndOffset;
            _tmpEndOffset = _cursor.getInt(_cursorIndexOfEndOffset);
            final String _tmpCachedAudioPath;
            if (_cursor.isNull(_cursorIndexOfCachedAudioPath)) {
              _tmpCachedAudioPath = null;
            } else {
              _tmpCachedAudioPath = _cursor.getString(_cursorIndexOfCachedAudioPath);
            }
            _item = new SentenceEntity(_tmpId,_tmpDocumentId,_tmpSentenceIndex,_tmpText,_tmpStartOffset,_tmpEndOffset,_tmpCachedAudioPath);
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
  public Object getSentences(final String documentId,
      final Continuation<? super List<SentenceEntity>> $completion) {
    final String _sql = "SELECT * FROM sentences WHERE documentId = ? ORDER BY sentenceIndex ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, documentId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<SentenceEntity>>() {
      @Override
      @NonNull
      public List<SentenceEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfDocumentId = CursorUtil.getColumnIndexOrThrow(_cursor, "documentId");
          final int _cursorIndexOfSentenceIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "sentenceIndex");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfStartOffset = CursorUtil.getColumnIndexOrThrow(_cursor, "startOffset");
          final int _cursorIndexOfEndOffset = CursorUtil.getColumnIndexOrThrow(_cursor, "endOffset");
          final int _cursorIndexOfCachedAudioPath = CursorUtil.getColumnIndexOrThrow(_cursor, "cachedAudioPath");
          final List<SentenceEntity> _result = new ArrayList<SentenceEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SentenceEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpDocumentId;
            _tmpDocumentId = _cursor.getString(_cursorIndexOfDocumentId);
            final int _tmpSentenceIndex;
            _tmpSentenceIndex = _cursor.getInt(_cursorIndexOfSentenceIndex);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final int _tmpStartOffset;
            _tmpStartOffset = _cursor.getInt(_cursorIndexOfStartOffset);
            final int _tmpEndOffset;
            _tmpEndOffset = _cursor.getInt(_cursorIndexOfEndOffset);
            final String _tmpCachedAudioPath;
            if (_cursor.isNull(_cursorIndexOfCachedAudioPath)) {
              _tmpCachedAudioPath = null;
            } else {
              _tmpCachedAudioPath = _cursor.getString(_cursorIndexOfCachedAudioPath);
            }
            _item = new SentenceEntity(_tmpId,_tmpDocumentId,_tmpSentenceIndex,_tmpText,_tmpStartOffset,_tmpEndOffset,_tmpCachedAudioPath);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
