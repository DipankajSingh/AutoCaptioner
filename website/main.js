// --- Scroll Reveal Logic ---
// We use IntersectionObserver as a fallback for browsers that don't support view() timelines
// (Though in CSS we used @supports to prefer view() if available)
const revealElements = document.querySelectorAll('.reveal');

const revealObserver = new IntersectionObserver((entries, observer) => {
  entries.forEach(entry => {
    if (entry.isIntersecting) {
      entry.target.classList.add('active');
      // Optional: stop observing once revealed
      observer.unobserve(entry.target);
    }
  });
}, {
  root: null,
  threshold: 0.1, // Trigger when 10% of element is visible
  rootMargin: "0px 0px -50px 0px"
});

// Check if browser supports animation-timeline: view(). If not, use JS observer.
if (!CSS.supports('animation-timeline: view()')) {
  revealElements.forEach(el => revealObserver.observe(el));
}


// --- Caption Animation Logic inside Mockup ---
const captionBox = document.getElementById('caption-box');
if (captionBox) {
  const words = captionBox.querySelectorAll('.caption-word');
  let currentWordIndex = 0;
  
  // A simple function to cycle through highlighting words one by one
  function animateCaptions() {
    // Remove active class from all
    words.forEach(word => word.classList.remove('active'));
    
    // Add active class to current
    if (words[currentWordIndex]) {
      words[currentWordIndex].classList.add('active');
    }
    
    // Move to next word
    currentWordIndex++;
    
    // Reset if we reach the end, wait a bit before restarting
    if (currentWordIndex >= words.length) {
      currentWordIndex = 0;
      setTimeout(animateCaptions, 2000); // 2 second pause at the end
    } else {
      // Pace: ~350ms per word
      setTimeout(animateCaptions, 350); 
    }
  }
  
  // Start animation loop
  animateCaptions();
}
