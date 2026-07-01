import React from 'react';
import Header from './components/Header';
import Hero from './components/Hero';
import Scrollytelling from './components/Scrollytelling';
import Testimonials from './components/Testimonials';
import FAQ from './components/FAQ';
import Footer from './components/Footer';

function App() {
  return (
    <>
      <Header />
      <main>
        <Hero />
        <Scrollytelling />
        <Testimonials />
        <FAQ />
        
        <section className="py-24 border-t border-glassBorder text-center bg-[#080b12]" id="download">
          <div className="container mx-auto px-8 relative z-10">
            <h2 className="text-4xl font-bold mb-4">Stop Reading, Start Captioning</h2>
            <p className="text-xl text-textSecondary mb-8 max-w-2xl mx-auto">Join the closed beta and experience truly private AI. It's fast, it's free (for now), and it doesn't judge your camera presence.</p>
            <a href="#" className="btn btn-primary text-lg px-8 py-4 shadow-[0_0_40px_rgba(56,189,248,0.4)]">Download on Google Play</a>
          </div>
        </section>
      </main>
      <Footer />
    </>
  );
}

export default App;
