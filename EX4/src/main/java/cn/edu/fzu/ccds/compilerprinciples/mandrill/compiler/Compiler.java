package cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler;

import java.io.IOException;
import java.io.InputStream;

public interface Compiler {

    String compile(InputStream inputStream) throws IOException;
}
