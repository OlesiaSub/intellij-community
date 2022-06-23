import java.util.Optional;
import java.util.stream.Stream;

public class DefaultInterfaceMethod {
  public static void main(String[] args) {
    // Breakpoint!
    Stream.of(new MyClass(1.0), new MyClass(3.0), new MyClass(2.0))
        .max(MyInterface::compareTo);
  }
  interface MyInterface extends Comparable<MyInterface> {
    Double getWeight();

    @Override
    default int compareTo(MyInterface other) {
      return Double.compare(getWeight(), other.getWeight());
    }
  }

  static class MyClass implements MyInterface {
    private final Double weight;
    MyClass(Double weight) {
      this.weight = weight;
    }
    @Override
    public Double getWeight() {
      return weight;
    }
  }
}