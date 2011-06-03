package hudson.util.export;


import java.io.IOException;
import java.io.Writer;

/**
 * Export flavor.
 *
 * @author Kohsuke Kawaguchi
 */
public enum Flavor {
    JSON("application/javascript;charset=UTF-8") {
        
        public DataWriter createDataWriter(Object bean, Writer w) throws IOException {
            return new JSONDataWriter(w);
        }
        
        public DataWriter createDataWriter(Writer w) throws IOException {
            return new JSONDataWriter(w);
        }
    },
     
    XML("application/xml;charset=UTF-8") {
         
        public DataWriter createDataWriter(Object bean, Writer w) throws IOException {
            return new XMLDataWriter(bean,w);
        }
    };

    /**
     * Content-type of this flavor, including charset "UTF-8".
     */
    public final String contentType;

    Flavor(String contentType) {
        this.contentType = contentType;
    }

    
    public abstract DataWriter createDataWriter(Object bean, Writer w) throws IOException;
}
