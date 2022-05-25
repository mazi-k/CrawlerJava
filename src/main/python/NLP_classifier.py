from elasticsearch import Elasticsearch
import pandas as pd
import numpy as np
import re, string
import nltk
from nltk.tokenize import word_tokenize
from nltk.corpus import stopwords
from nltk.tokenize import word_tokenize
from nltk.stem import SnowballStemmer
from nltk.corpus import wordnet
from nltk.stem import WordNetLemmatizer
nltk.download('punkt')
nltk.download('averaged_perceptron_tagger')
nltk.download('wordnet')
#для построения моделей
from sklearn.model_selection import train_test_split
from sklearn.linear_model import LogisticRegression
from sklearn.naive_bayes import MultinomialNB
from sklearn.metrics import classification_report, f1_score, accuracy_score, confusion_matrix
from sklearn.metrics import roc_curve, auc, roc_auc_score
# для сбора "пакета" слов
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.feature_extraction.text import CountVectorizer
#для встраивания слов
import gensim
from gensim.models import Word2Vec

es = Elasticsearch('http://127.0.0.1:9200')
res = es.search(index="womhit_logs", body={"query":{"match_all":{}}, "size":228339})

#половина текстов на тренировку, половина - на тестирование
i = 0
for hit in res['hits']['hits']:
    if i < res.size()/2:
        df_train.add(hit["_source"])
    else:
        df_test.add(hit["_source"])
    i += 1
x = df_train['target'].value_counts()
df_train.isna().sum()

# количество слов
df_train['word_count'] = df_train['text'].apply(lambda x: len(str(x).split()))
print(df_train[df_train['target'] == 1]['word_count'].mean())
print(df_train[df_train['target'] == 0]['word_count'].mean())

# построение графиков
fig,(ax1,ax2) = plt.subplots(1,2,figsize = (10,4))
train_words = df_train[df_train['target'] == 1]['word_count']
ax1.hist(train_words,color = 'red')
ax1.set_title('Disaster tweets')
train_words = df_train[df_train['target'] == 0]['word_count']
ax2.hist(train_words,color='green')
ax2.set_title('Non-disaster tweets')
fig.suptitle('Words per tweet')
plt.show()

# подсчитать количество символов
df_train['char_count'] = df_train['text'].apply(lambda x: len(str(x)))
print(df_train[df_train['target'] == 1]['char_count'].mean())
print(df_train[df_train['target'] == 0]['char_count'].mean())

#преобразовать в нижний регистр, убрать и удалить знаки препинания
def preprocess(text):
    text = text.lower()
    text = text.strip()
    text = re.compile('<.*?>').sub('', text)
    text = re.compile('[%s]' % re.escape(string.punctuation)).sub(' ', text)
    text = re.sub('\s+', ' ', text)
    text = re.sub(r'\[[0-9]*\]',' ',text)
    text = re.sub(r'[^\w\s]', '', str(text).lower().strip())
    text = re.sub(r'\d',' ',text)
    text = re.sub(r'\s+',' ',text)
    return text


# удаление стоп-слов
def stopword(string):
    a = [i for i in string.split() if i not in stopwords.words('russian')]
    return ' '.join(a)

# разбиение на символы
wl = WordNetLemmatizer()

# вспомогательная функция для Natural Language Toolkit
def get_wordnet_pos(tag):
    if tag.startswith('П'):
        return wordnet.ADJ
    elif tag.startswith('Г'):
        return wordnet.VERB
    elif tag.startswith('С'):
        return wordnet.NOUN
    elif tag.startswith('Р'):
        return wordnet.ADV
    else:
        return wordnet.NOUN

# разбиение на предложения
def lemmatizer(string):
    word_pos_tags = nltk.pos_tag(word_tokenize(string))
    a = [wl.lemmatize(tag[0], get_wordnet_pos(tag[1])) for idx, tag in enumerate(word_pos_tags)]
    return " ".join(a)

def finalpreprocess(string):
        return lemmatizer(stopword(preprocess(string)))
    df_train['clean_text'] = df_train['text'].apply(lambda x: finalpreprocess(x))
    df_train.head()

# разделение обучающего набора данных на обучающий и тестовый
X_train, X_test, y_train, y_test = train_test_split(df_train["clean_text"],df_train["target"],test_size=0.2,shuffle=True)

# Word2Vec запуск с разбитыми предложеняими
X_train_tok = [nltk.word_tokenize(i) for i in X_train]
X_test_tok = [nltk.word_tokenize(i) for i in X_test]

tfidf_vectorizer = TfidfVectorizer(use_idf = True)
X_train_vectors_tfidf = tfidf_vectorizer.fit_transform(X_train)
X_test_vectors_tfidf = tfidf_vectorizer.transform(X_test)

#сборка модели Word2Vec
class MeanEmbeddingVectorizer(object):
    def __init__(self, word2vec):
        self.word2vec = word2vec
        # если текст пуст, вернуть вектор нулей с той же размерностью, что и все остальные векторы
        self.dim = len(next(iter(word2vec.values())))
def fit(self, X, y):
        return self
def transform(self, X):
        return np.array([
            np.mean([self.word2vec[w] for w in words if w in self.word2vec]
                    or [np.zeros(self.dim)], axis=0)
            for words in X
        ])
w2v = dict(zip(model.wv.index2word, model.wv.syn0)) df['clean_text_tok']=[nltk.word_tokenize(i) for i in df['clean_text']]
model = Word2Vec(df['clean_text_tok'],min_count=1)
modelw = MeanEmbeddingVectorizer(w2v)

# преобразование текста в числовые данные с помощью Word2Vec
X_train_vectors_w2v = modelw.transform(X_train_tok)
X_val_vectors_w2v = modelw.transform(X_test_tok)

#была подготовлена модель для Word2Vec для дальнейшего использования