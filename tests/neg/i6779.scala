type F[T]
type G[T]
type Stuff
given Stuff = ???

def [T](x: T).f(using Stuff): F[T] = ???


def g1[T](x: T): F[G[T]] = x.f(using summon[Stuff]) // error

def g2[T](x: T): F[G[T]] = x.f // error

def g3[T](x: T): F[G[T]] = f(x)(using summon[Stuff]) // error
